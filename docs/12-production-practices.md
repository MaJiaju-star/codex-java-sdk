# 12. 生产环境最佳实践

[上一章](11-errors-and-troubleshooting.md) · [返回目录](README.md) · [下一章：API 速查](13-api-reference.md)

## 1. 生命周期设计

推荐把 `CodexClient` 作为受管理的长生命周期组件：

```java
public final class CodexService implements AutoCloseable {
    private final CodexClient client;

    public CodexService(CodexClientConfig config) {
        this.client = CodexClient.create(config);
    }

    public CodexThread start(Path repository) {
        return client.startThread(ThreadOptions.builder()
                .workingDirectory(repository)
                .sandbox(ThreadOptions.Sandbox.WORKSPACE_WRITE)
                .approvalPolicy(ThreadOptions.ApprovalPolicy.ON_REQUEST)
                .build());
    }

    @Override
    public void close() {
        client.close();
    }
}
```

不要为每个 HTTP 请求启动一个新 app-server，除非确实需要进程级隔离。

## 2. 启动健康检查

`CodexClient.create()` 成功并不代表模型、MCP 和目标仓库都可用，但至少可以确认子进程和握手正常。

```java
try (CodexClient codex = CodexClient.create(config)) {
    JsonNode metadata = codex.metadata();
    if (!metadata.isObject()) {
        throw new IllegalStateException("Invalid Codex metadata");
    }
}
```

业务就绪检查还可以调用 `models(false)`，但应考虑账户和网络依赖，不要以过高频率调用。

## 3. 固定版本

记录并固定：

- Java SDK 版本；
- Codex CLI 版本；
- JDK 版本；
- Maven 依赖锁定策略；
- app-server 实验能力开关。

升级 CLI 时先在预发布环境运行：

- 初始化握手测试；
- Thread 创建/恢复测试；
- Turn 事件测试；
- 审批双向请求测试；
- 原始 JSON-RPC 契约测试。

## 4. 工作区隔离

自动化服务应为每个任务创建独立工作副本：

```text
/workspaces/task-1001
/workspaces/task-1002
/workspaces/task-1003
```

不要让多个不相关任务并发修改同一目录。即使它们使用不同 Thread，文件系统仍是共享状态。

## 5. 幂等性

Codex Turn 可能执行命令和修改文件，不能假设失败后安全重试。重试前检查：

- 是否已经产生文件修改；
- 命令是否有外部副作用；
- Turn 是否实际上仍在运行；
- 是否应恢复原 Thread 或从干净工作区重新开始。

只对读取型 JSON-RPC 请求和明确无副作用的操作做自动重试。

## 6. 观测指标

建议记录：

```text
codex_client_start_total
codex_client_start_failures_total
codex_rpc_duration_seconds{method}
codex_rpc_errors_total{method,code}
codex_turn_duration_seconds{status}
codex_turn_items_total{type}
codex_turn_interruptions_total
codex_approval_requests_total{method,decision}
codex_tool_output_bytes_total{kind}
codex_tool_failures_total{kind,status}
codex_active_turns
```

日志关联字段：

```text
application_request_id
application_task_id
codex_thread_id
codex_turn_id
codex_item_id
codex_cli_version
```

## 7. 事件持久化

不要把所有 delta 永久存储为业务真相。推荐：

- delta：用于实时展示，可短期缓存；
- `item/completed`：保存完整重要 Item；
- `turn/completed`：保存最终状态；
- `TurnResult.finalResponse`：保存最终用户可见回答；
- 原始事件：按诊断需求设置有限保留期。

## 8. 日志脱敏

原始事件和 JSON-RPC 参数可能包含：

- 源码片段；
- 文件路径；
- 命令行参数；
- 环境信息；
- 用户文本；
- 工具响应。

在集中日志前进行字段级脱敏。不要默认记录完整 `params.toPrettyString()`。

工具输出是特别容易失控的数据源。即使 SSE 历史条数有上限，单条输出仍可能很大；
应同时限制单个增量、单个 `itemId` 的累计字节数和每个用户的总输出速率。ANSI
控制序列、退格符和终端超链接也应在进入 HTML 前删除或安全解析。

## 9. 限流和背压

在应用入口限制：

- 同时活动的 Turn 数；
- 每个用户的任务数；
- 每个仓库的并发写任务；
- 队列等待时间；
- 单个任务总时长；
- 输入文件大小。

app-server 可能返回 `-32001` 过载错误。应用层限流比收到错误后重试更稳定。

## 10. 安全默认值

面向不受信请求的服务建议：

```java
ThreadOptions.builder()
        .sandbox(ThreadOptions.Sandbox.READ_ONLY)
        .approvalPolicy(ThreadOptions.ApprovalPolicy.NEVER)
        .build();
```

只有明确的写任务才升级到 `WORKSPACE_WRITE`，并使用隔离工作区与自定义审批处理器。

内网模型服务还应隔离认证来源：使用 `openAiCompatibleProvider(...)` 注册
`requires_openai_auth = false` 的独立 Provider，并通过它声明的环境变量注入 API Key。
不要只覆盖内置 Provider 的 `baseUrl`；否则部署机器上残留的 Codex CLI 登录态仍可能成为
模型请求的认证来源。配置示例见[第三章](03-configuration.md)。

多用户服务必须按用户隔离 `CodexClient`、API Key、`CODEX_HOME` 和工作区。API Key 是
app-server 进程级状态，Thread 不是密钥隔离边界。还应通过
`shell_environment_policy.filters.<KEY_NAME>="exclude"` 禁止 Shell 工具继承模型密钥。
密钥轮换时重建对应 Client，空闲 Client 应按策略回收。

## 11. 优雅关闭

```java
void shutdown(CodexClient client, Collection<CodexTurn> activeTurns) {
    for (CodexTurn turn : activeTurns) {
        try {
            turn.interrupt();
        } catch (RuntimeException error) {
            logger.warn("Failed to interrupt turn {}", turn.id(), error);
        }
    }

    client.close();
}
```

在容器终止宽限期内给活动 Turn 一定时间完成中断和结果持久化。

## 12. 契约测试

SDK 自身的测试使用模拟 app-server 子进程，验证：

- initialize/initialized；
- Thread 和 Turn 请求；
- 服务端审批请求；
- 事件先于 `turn/start` 响应到达；
- Flow 事件顺序；
- 最终回答和 usage 汇总；
- 审批处理器运行在虚拟线程。

业务项目也应建立自己的协议测试，覆盖所依赖的原始方法和字段。

## 13. 模拟 app-server 的测试思路

测试进程读取 stdin 每行 JSON，根据 `method` 写回响应：

```java
while ((line = reader.readLine()) != null) {
    JsonNode request = mapper.readTree(line);
    String method = request.path("method").asText();

    if (method.equals("initialize")) {
        writeResponse(request.path("id"), mapper.createObjectNode()
                .put("userAgent", "test-server"));
    }
}
```

使用 `CodexClientConfig.command(...)` 启动测试服务器，可以完全离线验证 Java 侧传输逻辑。

## 14. 发布前检查清单

- [ ] JDK 25 和 Maven Enforcer 检查通过；
- [ ] `mvn clean test` 通过；
- [ ] 真实 CLI smoke test 通过；
- [ ] CLI 与 SDK 版本已记录；
- [ ] 默认审批策略已显式评审；
- [ ] 每个用户拥有独立的 Client、API Key、`CODEX_HOME` 和工作区；
- [ ] 模型 API Key 已从 Shell 工具环境中排除；
- [ ] Client 空闲回收和 API Key 轮换重建策略已经验证；
- [ ] 审批 ID 已绑定用户、网页会话和 Codex Thread，且只能处理一次；
- [ ] 待审批请求具有超时，关闭会话时会全部取消；
- [ ] 工作区和本地路径已限制；
- [ ] Future 与 Turn 有业务总超时；
- [ ] 中断和关闭流程经过测试；
- [ ] 日志已脱敏；
- [ ] 工具输出具有单增量、单 Item 和用户级总量限制；
- [ ] 原始 JSON-RPC 方法有契约测试；
- [ ] 监控、限流和告警已配置。

---

[上一章](11-errors-and-troubleshooting.md) · [返回目录](README.md) · [下一章：API 速查](13-api-reference.md)
