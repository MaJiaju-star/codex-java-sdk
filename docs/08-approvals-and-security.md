# 08. 审批、安全与沙箱

[上一章](07-inputs-and-structured-output.md) · [返回目录](README.md) · [下一章：异步与并发](09-async-and-concurrency.md)

Codex 能执行命令和修改文件，因此审批不是普通回调，而是安全边界的一部分。

## 1. 默认审批行为

当前 Java SDK 默认接受：

- `item/commandExecution/requestApproval`
- `item/fileChange/requestApproval`

其他服务端请求仍交给原始 `serverRequestHandler`。

这让本地开发快速可用，但不适合所有生产场景。特别是当应用处理不受信用户输入或操作重要仓库时，应显式配置审批处理器。

生产应用应显式写出审批行为，即使它确实需要全部允许：

```java
CodexClientConfig config = CodexClientConfig.builder()
        .approvalHandler(ApprovalHandler.acceptAll())
        .build();
```

## 2. 强类型审批处理器

`ApprovalHandler` 可以同步完成，也可以返回稍后由网页用户确认的
`CompletionStage`：

```java
CompletionStage<ApprovalRequest.Decision> requestApproval(ApprovalRequest request)
```

示例：命令和文件变更分别进入业务策略，未知情况默认拒绝：

```java
CodexClientConfig config = CodexClientConfig.builder()
        .approvalHandler(request -> {
            boolean allowed = switch (request) {
                case ApprovalRequest.Command command ->
                        commandPolicyAllows(command.context());
                case ApprovalRequest.FileChange change ->
                        filePolicyAllows(change.context(), change.grantRoot());
            };
            ApprovalRequest.Decision decision = allowed
                    ? ApprovalRequest.Decision.ACCEPT
                    : ApprovalRequest.Decision.DECLINE;
            return CompletableFuture.completedFuture(decision);
        })
        .build();
```

`ApprovalRequest.Command` 和 `ApprovalRequest.FileChange` 都带有
`ToolCallContext`，包含 Thread、Turn、Item、命令、工作目录、工具类型和原始参数。

## 3. 工具前后拦截器

拦截器在最终 `ApprovalHandler` 之前按注册顺序执行。返回 `Continue` 会继续调用
下一个拦截器；返回 `Decide` 会短路后续拦截器和审批器。

```java
ToolInterceptor workspaceGuard = new ToolInterceptor() {
    @Override
    public CompletionStage<BeforeResult> beforeToolCall(ApprovalRequest request) {
        if (!isInsideWorkspace(request.context())) {
            return CompletableFuture.completedFuture(
                    new BeforeResult.Decide(ApprovalRequest.Decision.DECLINE));
        }
        return CompletableFuture.completedFuture(new BeforeResult.Continue());
    }

    @Override
    public CompletionStage<Void> afterToolCall(ToolCallResult result) {
        auditResult(result);
        return CompletableFuture.completedFuture(null);
    }
};

CodexClientConfig config = CodexClientConfig.builder()
        .toolInterceptor(workspaceGuard)
        .approvalHandler(ApprovalHandler.acceptAll())
        .build();
```

完成回调按注册的相反顺序执行。前置拦截器或审批器超时、抛出异常时，SDK
会失败关闭并返回 `DECLINE`；完成后的异常不会改变已经结束的工具调用。

审批前置拦截只适用于 app-server 真正发出审批请求的命令和文件修改。
`item/started` 已经是生命周期通知，不能用于阻止执行。

### Web 前端异步审批

`ApprovalHandler` 不需要阻塞 Solon/Servlet 请求线程。可以把待审批 Future 保存到
会话状态，先通过 SSE 通知浏览器，等用户点击后再完成它：

```java
private final ConcurrentHashMap<String, CompletableFuture<ApprovalRequest.Decision>> pending =
        new ConcurrentHashMap<>();

CompletionStage<ApprovalRequest.Decision> requestApproval(ApprovalRequest request) {
    String approvalId = UUID.randomUUID().toString();
    CompletableFuture<ApprovalRequest.Decision> future = new CompletableFuture<>();
    pending.put(approvalId, future);
    LinkedHashMap<String, Object> event = new LinkedHashMap<>();
    event.put("approvalId", approvalId);
    event.put("itemId", request.context().itemId());
    event.put("command", request.context().command());
    sse.publish("approval.requested", event);
    future.completeOnTimeout(ApprovalRequest.Decision.CANCEL, 9, TimeUnit.MINUTES)
            .whenComplete((decision, error) -> {
                pending.remove(approvalId, future);
                sse.publish("approval.resolved", Map.of(
                        "approvalId", approvalId,
                        "decision", error == null ? decision.wireValue() : "cancel"));
            });
    return future;
}

void resolve(String approvalId, String wireDecision) {
    ApprovalRequest.Decision decision = switch (wireDecision) {
        case "accept" -> ApprovalRequest.Decision.ACCEPT;
        case "acceptForSession" -> ApprovalRequest.Decision.ACCEPT_FOR_SESSION;
        case "decline" -> ApprovalRequest.Decision.DECLINE;
        case "cancel" -> ApprovalRequest.Decision.CANCEL;
        default -> throw new IllegalArgumentException("未知审批决定");
    };
    CompletableFuture<ApprovalRequest.Decision> future = pending.get(approvalId);
    if (future == null || !future.complete(decision)) {
        throw new IllegalArgumentException("审批不存在、已处理或已超时");
    }
}
```

实际接口还应校验 `approvalId` 属于当前登录用户和当前网页会话，并显式拒绝未知
`wireDecision`。不要让前端自行提供 `threadId` 来定位并批准其他用户的会话。关闭会话
时还应以 `CANCEL` 完成所有待处理 Future，避免 app-server 一直等待。

四种决定的协议值可由 `Decision.wireValue()` 获取。`Decision.fromWireValue(...)` 对未知值
采取失败关闭策略并返回 `DECLINE`；HTTP API 若需要区分客户端拼写错误，应像上例一样
先显式校验，而不是静默转换成拒绝。

## 4. 工具观察器

只需要日志、指标和 UI 时应使用 `ToolObserver`，不要让观测代码参与授权：

```java
ToolObserver observer = new ToolObserver() {
    @Override
    public void onApprovalRequested(ApprovalRequest request) {
        auditLogger.info("approval requested: {}", sanitize(request));
    }

    @Override
    public void onStarted(ToolCallContext context) {
        auditLogger.info("tool started: {}", sanitize(context));
    }

    @Override
    public void onOutput(ToolCallContext context, String delta) {
        terminalSink.append(context.itemId(), delta);
    }

    @Override
    public void onCompleted(ToolCallResult result) {
        auditLogger.info("tool completed: {}", sanitize(result));
    }
};
```

观察器异常会被隔离，不会中断 JSON-RPC 读取或工具执行。回调在保序的虚拟线程
执行器中运行；观察器仍应快速返回。`onApprovalRequested` 只是通知，不能代替
`ApprovalHandler` 或 `ToolInterceptor` 的授权决定。当前 `onOutput` 用于命令输出；
文件补丁和 MCP 进度可结合原始枚举事件展示。

MCP Server 注册时不要把 bearer token 写入 `httpHeader(...)` 或原始
`configOverride(...)`。优先使用环境变量引用：

```java
CodexClientConfig.builder()
        .environment("MCP_TOKEN", token)
        .mcpServer("internal", McpServerConfig.streamableHttp(mcpUri)
                .bearerTokenEnvVar("MCP_TOKEN")
                .envHttpHeader("X-Api-Key", "MCP_API_KEY")
                .defaultToolsApprovalMode(McpToolApprovalMode.PROMPT)
                .tool("read", McpToolConfig.approval(McpToolApprovalMode.APPROVE))
                .tool("write", McpToolConfig.approval(McpToolApprovalMode.WRITES))
                .build())
        .build();
```

`httpHeader(...)` 适合非敏感固定 Header；`envHttpHeader(header, envName)` 的第二个参数是
环境变量名称，不是密钥值。MCP 配置会出现在 app-server 命令行的 `--config` 参数中，因此
不要把任何凭据直接放进配置字符串。stdio 的 `env(name, value)` 同样只适合非敏感固定值；
密钥应通过 `CodexClientConfig.environment(...)` 注入，再用 `McpEnvVar.inherit(...)` 引用变量名。

`ToolCallResult.successful()` 不只检查 `error` 字段：完成 Item 的状态为 `failed` 或
`declined` 时，即使没有独立错误对象也会返回 `false`。日志中应删除密钥、认证头、
敏感文件内容和个人数据。

## 5. 原始处理器兼容入口

原来的 `serverRequestHandler((method, params) -> ...)` 仍然可用。设置它时，SDK
会把命令和文件审批结果适配为 `ApprovalRequest.Decision`；其他未知双向请求仍直接
交给该处理器。这适合尚未强类型化的新协议，但新代码优先使用 `ApprovalHandler`。

## 6. 审批处理运行在线程模型中

SDK 在虚拟线程执行审批处理器，因此阻塞式数据库查询不会占用传统平台线程。但处理器仍应尽快返回，因为 app-server 正在等待 JSON-RPC 响应。

建议：

- 为数据库和远程策略服务设置短超时；
- 超时或异常时默认拒绝；
- 不在处理器中等待同一个 Turn 完成；
- 不从处理器再次发起会造成循环依赖的 Codex 请求。

## 7. 沙箱与审批是两层控制

```text
Sandbox：操作最多能够触达哪里
ApprovalPolicy：操作需要在什么时候询问
ApprovalHandler / ToolInterceptor：询问发生后由谁决定
```

例如：

```java
ThreadOptions.builder()
        .sandbox(ThreadOptions.Sandbox.WORKSPACE_WRITE)
        .approvalPolicy(ThreadOptions.ApprovalPolicy.ON_REQUEST)
        .build();
```

表示代理可以在工作区内写入；需要额外权限时可能发出审批请求。即使处理器接受，实际操作仍受 app-server 和系统权限限制。

## 8. 推荐安全组合

### 代码解释和审查

```java
.sandbox(ThreadOptions.Sandbox.READ_ONLY)
.approvalPolicy(ThreadOptions.ApprovalPolicy.NEVER)
```

### 本地交互式开发

```java
.sandbox(ThreadOptions.Sandbox.WORKSPACE_WRITE)
.approvalPolicy(ThreadOptions.ApprovalPolicy.ON_REQUEST)
```

配合展示详情并由用户确认的审批处理器。

### 自动化修复流水线

```java
.sandbox(ThreadOptions.Sandbox.WORKSPACE_WRITE)
.approvalPolicy(ThreadOptions.ApprovalPolicy.NEVER)
```

仅在隔离的临时工作区中使用，并在提交变更前运行测试、静态扫描和 diff 策略检查。

### 完全访问

`DANGER_FULL_ACCESS` 只应用于已隔离且任务确实需要访问工作区外资源的执行环境。不要仅为了避免审批失败就提升到完全访问。

## 9. 处理未知服务端请求

app-server 还可能发送用户输入、权限申请、MCP elicitation 或动态工具调用等双向请求。当前 SDK 不提供这些请求的强类型模型。

安全做法是默认拒绝或返回受控错误，而不是自动构造成功响应。可以先记录方法名：

```java
default -> {
    securityLogger.warn("Unsupported Codex server request: {}", method);
    yield mapper.createObjectNode();
}
```

为某个方法添加支持前，应检查对应 CLI 版本生成的 JSON Schema。

## 10. 保护工作目录

- 使用独立工作副本，不直接操作生产部署目录；
- 服务账号只授予必要权限；
- 校验传入的 `Path` 位于允许根目录；
- 避免把用户输入直接拼接到启动命令；
- 不允许用户控制 `CodexClientConfig.command(...)`；
- 将密钥和凭据放在工作区之外；
- 对最终 diff 做独立检查。

## 11. 防止命令注入

`command(List<String>)` 直接传给 `ProcessBuilder`。参数列表本身比单个 shell 字符串安全，但 Windows 默认命令经由 `cmd.exe` 启动 Codex。不要把不受信字符串放入启动命令。

Turn prompt 不是 shell 命令，但模型可能根据 prompt 选择执行命令，所以仍必须依靠沙箱与审批控制风险。

## 12. 关闭客户端

始终使用 try-with-resources：

```java
try (CodexClient codex = CodexClient.create(config)) {
    // 使用 SDK
}
```

`close()` 会关闭事件发布器、使未完成请求失败、通知活动 Turn、关闭 stdin、终止 app-server 并停止虚拟线程执行器。

---

[上一章](07-inputs-and-structured-output.md) · [返回目录](README.md) · [下一章：异步与并发](09-async-and-concurrency.md)
