# 11. 异常处理与故障排查

[上一章](10-raw-json-rpc.md) · [返回目录](README.md) · [下一章：生产实践](12-production-practices.md)

## 1. 异常层次

```text
RuntimeException
└── CodexException
    ├── CodexTransportException
    ├── CodexTimeoutException
    └── JsonRpcException
        ├── JsonRpcParseException
        ├── InvalidRequestException
        ├── MethodNotFoundException
        ├── InvalidParamsException
        ├── InternalRpcException
        └── ServerBusyException
            └── RetryLimitExceededException
```

### CodexException

覆盖进程启动、传输中断、超时、协议响应缺失和 Turn 失败等 SDK 错误。

### JsonRpcException

表示 app-server 返回 JSON-RPC error：

```java
try {
    codex.request("thread/read", params);
} catch (JsonRpcException error) {
    System.err.println("code=" + error.code());
    System.err.println("message=" + error.getMessage());
    System.err.println("data=" + error.data());
}
```

## 2. 统一解包异步异常

```java
static Throwable rootCause(Throwable error) {
    Throwable current = error;
    while ((current instanceof CompletionException
            || current instanceof ExecutionException)
            && current.getCause() != null) {
        current = current.getCause();
    }
    return current;
}
```

使用：

```java
turn.resultAsync().whenComplete((result, error) -> {
    if (error == null) {
        saveResult(result);
        return;
    }

    Throwable cause = rootCause(error);
    if (cause instanceof JsonRpcException rpc) {
        handleRpcError(rpc);
    } else if (cause instanceof CodexException codexError) {
        handleCodexError(codexError);
    } else {
        handleUnexpectedError(cause);
    }
});
```

示例中的 `saveResult` 和 `handle*` 是应用方法。

## 3. 无法启动 Codex

典型消息：

```text
Unable to start Codex command: [...]
```

检查：

1. `codex --version` 是否在启动 Java 的同一环境中可用；
2. Windows 服务账号的 `PATH` 是否包含 npm 或 Codex 目录；
3. `codexExecutable(...)` 路径是否存在；
4. 文件是否具有执行权限；
5. `CodexClientConfig.workingDirectory(...)` 是否存在；
6. 容器内是否安装 CLI。

服务进程的环境与交互式终端常常不同。优先使用绝对 `codexExecutable` 路径。

## 4. stdout 解析失败或连接关闭

app-server stdout 必须只包含 JSONL 协议消息。包装脚本的普通输出会导致解析失败。

错误日志应写 stderr：

```shell
echo "starting codex" 1>&2
exec codex app-server
```

SDK 会保留最近最多 400 行 stderr；传输异常消息中最多附带约 2000 个字符，便于定位子进程失败。

## 5. 初始化超时

`CodexClient.create(...)` 会等待 `initialize` 响应。超时可能来自：

- 命令启动的不是 app-server；
- CLI 首次启动等待交互输入；
- stdout 被包装程序缓冲；
- 版本不支持当前握手参数；
- 子进程在加载配置或 MCP 服务时卡住。

提高请求超时只能缓解慢启动：

```java
.requestTimeout(Duration.ofMinutes(2))
```

不要用无限超时掩盖错误启动命令。

## 6. Thread 找不到

收到 Thread 不存在相关 JSON-RPC 错误时，检查：

- Thread ID 是否完整；
- 是否使用了原来的 `CODEX_HOME`；
- Thread 是否为 `ephemeral`；
- Thread 是否已归档；
- 运行账号是否变化；
- 会话目录是否被清理。

## 7. Turn 一直不完成

检查流式事件：

```java
CodexTurn turn = thread.startTurn("执行任务");
turn.events().subscribe(new LoggingSubscriber());

try {
    TurnResult result = turn.resultAsync().get(15, TimeUnit.MINUTES);
} catch (TimeoutException error) {
    turn.interrupt();
    throw error;
}
```

可能原因：

- 等待审批，但处理器没有返回合法响应；
- 命令本身长时间运行；
- MCP 或网络工具挂起；
- Flow 订阅者不调用 `subscription.request(...)`，导致背压阻塞 Turn 事件消费；
- app-server 已断开但应用忽略了 Future 异常。

## 8. “Turn events are already being consumed”

原因是同一个 Turn 启动了第二个事件消费者。正确写法：

```java
CodexTurn turn = thread.startTurn("任务");
turn.events().subscribe(singleSubscriber);
CompletableFuture<TurnResult> future = turn.resultAsync();
```

不要多次调用 `events().subscribe(...)`，也不要先 `await()` 再订阅。

## 9. 审批失败

如果自定义处理器抛出异常，SDK 会返回 JSON-RPC 内部错误。处理器应自行捕获业务异常并选择安全的拒绝结果：

```java
.serverRequestHandler((method, params) -> {
    try {
        return evaluateApproval(method, params);
    } catch (Exception error) {
        securityLogger.error("Approval evaluation failed", error);
        return mapper.createObjectNode().put("decision", "decline");
    }
})
```

## 10. JSON-RPC 常见错误码

| 错误码 | 常见含义 |
|---|---|
| `-32700` | JSON 解析错误 |
| `-32600` | 请求无效或当前状态不允许 |
| `-32601` | 方法不存在，可能是 CLI 版本不支持 |
| `-32602` | 参数无效或字段结构不匹配 |
| `-32603` | app-server 内部错误 |
| `-32001` | app-server 入口过载，稍后重试 |

是否重试取决于语义。参数错误和方法不存在不应盲目重试；过载和短暂传输问题可使用指数退避。

## 11. 过载自动重试

```java
CodexClientConfig config = CodexClientConfig.builder()
        .retryPolicy(new RetryPolicy(
                5,
                Duration.ofMillis(200),
                Duration.ofSeconds(3),
                2.0,
                0.2))
        .build();
```

SDK 默认最多尝试 3 次，延迟从 250 毫秒开始，2 倍增长、上限 2 秒并加入 20% 抖动。
只有 `-32001` 或错误数据中明确标记 `serverOverloaded`/`server_overloaded` 的响应会重试；
参数错误、方法不存在、内部错误和普通业务失败不会重试。同步 `request` 和异步
`requestAsync` 使用相同策略。

完全关闭自动重试：

```java
.retryPolicy(RetryPolicy.disabled())
```

## 12. Maven/JDK 问题

如果 Enforcer 报 Java 版本错误：

```shell
mvn -version
```

确认输出是 JDK 25，而不是只看 `java -version`。IDE 的 Maven Runner 也可能使用独立 JDK。

## 13. 最小诊断信息

提交问题时建议附带：

- OS 和架构；
- `java -version`、`mvn -version`；
- Codex CLI 版本；
- app-server 初始化 metadata；
- JSON-RPC 错误码和脱敏 data；
- Thread/Turn ID；
- stderr 尾部；
- 是否使用自定义 command、环境、沙箱和审批处理器。

不要附带 API Key、认证文件或未经脱敏的私有源码。

---

[上一章](10-raw-json-rpc.md) · [返回目录](README.md) · [下一章：生产实践](12-production-practices.md)
