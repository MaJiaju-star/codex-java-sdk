# 13. 公开 API 速查

[上一章](12-production-practices.md) · [返回目录](README.md) · [下一章：Solon SSE 完整案例](14-solon-sse-complete-example.md)

本章只描述当前源码已经公开的 Java API。app-server 的完整协议面请参考第十章。

## 1. CodexClient

入口类，实现 `AutoCloseable`。

### 创建

```java
static CodexClient create()
static CodexClient create(CodexClientConfig config)
```

两者都会立即启动子进程并完成初始化握手。

### 初始化信息

```java
JsonNode metadata()
```

返回 `initialize` 原始结果。

### 全局事件

```java
Flow.Publisher<CodexEvent> events()
```

只发布订阅后的连接事件；压力过大时可能丢弃全局副本。

### Thread

```java
CodexThread startThread()
CodexThread startThread(ThreadOptions options)

CodexThread resumeThread(String threadId)
CodexThread resumeThread(String threadId, ThreadOptions options)

CodexThread forkThread(String threadId)
CodexThread forkThread(String threadId, ThreadOptions options)

ThreadListResponse listThreads()
ThreadListResponse listThreads(ThreadListOptions options)
JsonNode listThreads(JsonNode params)

ThreadArchiveResponse archiveThread(String threadId)
ThreadUnarchiveResponse unarchiveThread(String threadId)
```

### 模型

```java
CodexModelList listModels()
CodexModelList listModels(boolean includeHidden)
JsonNode models(boolean includeHidden)
```

`listModels` 返回强类型的模型目录；`models` 保留为原始 JSON 兼容入口。

### 原始请求

```java
JsonNode request(String method, JsonNode params)
CompletableFuture<JsonNode> requestAsync(String method, JsonNode params)
```

### MCP 运行时

```java
McpClient mcp()
```

`McpClient` 提供 `listStatuses`、`reload`、`startOAuthLogin`、`readResource` 和
`callTool`。请求和响应类型位于 `io.github.majiajustar.codex.mcp`。

### Skills 与 Config

```java
SkillsClient skills()
ConfigClient config()
```

`SkillsClient` 提供 `list`、`setExtraRoots`、按名称或路径启用/禁用 Skill。
`ConfigClient` 提供 `read`、`write`、`batchWrite` 和 `requirements`。

### 关闭

```java
void close()
```

关闭后不能继续使用由该 Client 创建的 Thread 和 Turn。

## 2. CodexClientConfig

Java record 字段：

```java
List<String> command
Path workingDirectory
Map<String, String> environment
String clientName
String clientTitle
String clientVersion
boolean experimentalApi
Duration requestTimeout
BiFunction<String, JsonNode, JsonNode> serverRequestHandler
ApprovalHandler approvalHandler
List<ToolInterceptor> toolInterceptors
List<ToolObserver> toolObservers
RetryPolicy retryPolicy
```

Builder：

```java
CodexClientConfig.builder()
    .command(List<String>)
    .codexExecutable(Path)
    .workingDirectory(Path)
    .environment(String name, String value)
    .baseUrl(String)
    .apiKey(String)
    .openAiCompatibleProvider(OpenAiCompatibleProviderConfig)
    .modelProvider(String)
    .model(String)
    .modelReasoningEffort(CodexClientConfig.ReasoningEffort)
    .webSearch(CodexClientConfig.WebSearchMode)
    .approvalPolicy(ThreadOptions.ApprovalPolicy)
    .workspaceNetworkAccess(boolean)
    .configOverride(String)
    .configOverrides(List<String>)
    .mcpServer(String name, McpServerConfig config)
    .mcpServers(Map<String, McpServerConfig> configs)
    .clientInfo(String name, String title, String version)
    .experimentalApi(boolean)
    .requestTimeout(Duration)
    .serverRequestHandler(BiFunction<String, JsonNode, JsonNode>)
    .approvalHandler(ApprovalHandler)
    .toolInterceptor(ToolInterceptor)
    .toolObserver(ToolObserver)
    .retryPolicy(RetryPolicy)
    .build()
```

默认值：

| 配置 | 默认值 |
|---|---|
| 命令 | Windows 使用 `cmd.exe /d /c codex app-server`，其他平台使用 `codex app-server` |
| clientName | `codex_java_sdk` |
| clientTitle | `Codex Java SDK` |
| clientVersion | `0.0.3-SNAPSHOT` |
| experimentalApi | `true` |
| requestTimeout | 60 秒 |
| retryPolicy | 过载最多尝试 3 次，250 ms 起始指数退避 |
| 审批处理器 | 接受命令执行和文件修改审批 |

### OpenAI-compatible Provider

```java
OpenAiCompatibleProviderConfig.builder(String id)
    .name(String)
    .baseUrl(URI)
    .apiKeyEnvironmentVariable(String)
    .build()
```

`name` 默认等于 `id`，API Key 环境变量默认是 `OPENAI_API_KEY`。Provider 固定使用
Responses API，并生成 `requires_openai_auth = false`。将它传给
`CodexClientConfig.Builder.openAiCompatibleProvider(...)` 后会同时注册并选中该 Provider。

Provider ID 只能包含字母、数字、下划线和连字符；`baseUrl` 必须是绝对 HTTP(S) URI；
环境变量名称必须符合常规环境变量标识符格式。密钥值应通过 `apiKey(...)` 或
`environment(...)` 注入，不属于 `OpenAiCompatibleProviderConfig`，也不会写入命令行。

### MCP 配置

通过 `McpServerConfig.stdio(String)` 或 `McpServerConfig.streamableHttp(URI)` 创建。

两种 Builder 共享 `enabled`、`required`、两个 `Duration` 超时、`environmentId`、
`omitToolsFrom`、`scopes`、`oauthResource`、并行调用开关、工具白/黑名单、默认审批模式和单工具
审批配置。stdio 专用方法为 `args`、`env`、`envVar`、`cwd`；HTTP 专用方法为
`bearerTokenEnvVar`、`httpHeader`、`envHttpHeader`、`httpHeadersHelper`、`auth`、`oauth`。

环境变量引用使用 `McpEnvVar.inherit/local/remote`，工具审批使用
`McpToolConfig.approval(...)`，OAuth 使用 `McpOAuthConfig.builder()`。

## 3. ThreadOptions

```java
ThreadOptions.defaults()
ThreadOptions.builder()
```

Builder：

```java
.model(String)
.workingDirectory(Path)
.sandbox(ThreadOptions.Sandbox)
.approvalPolicy(ThreadOptions.ApprovalPolicy)
.ephemeral(boolean)
.developerInstructions(String)
.approvalsReviewer(ApprovalsReviewer)
.baseInstructions(String)
.config(JsonNode)
.modelProvider(String)
.personality(Personality)
.serviceName(String)
.serviceTier(String)
.sessionStartSource(ThreadStartSource)
.threadSource(String)
.additionalDirectory(Path)
.additionalDirectories(List<Path>)
.skipGitRepoCheck(boolean)
.granularApprovalPolicy(ThreadOptions.GranularApprovalPolicy)
.lastTurnId(String)
.build()
```

枚举：

```java
ThreadOptions.Sandbox.READ_ONLY
ThreadOptions.Sandbox.WORKSPACE_WRITE
ThreadOptions.Sandbox.DANGER_FULL_ACCESS

ThreadOptions.ApprovalPolicy.UNTRUSTED
ThreadOptions.ApprovalPolicy.ON_REQUEST
ThreadOptions.ApprovalPolicy.NEVER
```

## 4. CodexThread

```java
String id()
ThreadGoals goals()
```

同步执行：

```java
TurnResult run(String prompt)
TurnResult run(String prompt, CancellationToken cancellationToken)
TurnResult run(List<UserInput> input, TurnOptions options)
TurnResult run(
        List<UserInput> input,
        TurnOptions options,
        CancellationToken cancellationToken)
```

启动可控制 Turn：

```java
CodexTurn startTurn(String prompt)
CodexTurn startTurn(String prompt, CancellationToken cancellationToken)
CodexTurn startTurn(List<UserInput> input, TurnOptions options)
CodexTurn startTurn(
        List<UserInput> input,
        TurnOptions options,
        CancellationToken cancellationToken)
```

Thread 操作：

```java
JsonNode read(boolean includeTurns)
void setName(String name)
void compact()
ThreadArchiveResponse archive()
ThreadUnarchiveResponse unarchive()
```

## 5. TurnOptions

```java
TurnOptions.defaults()
TurnOptions.builder()
```

Builder：

```java
.model(String)
.workingDirectory(Path)
.reasoningEffort(String)
.sandbox(ThreadOptions.Sandbox)
.sandboxPolicy(SandboxPolicy)
.approvalPolicy(ThreadOptions.ApprovalPolicy)
.granularApprovalPolicy(ThreadOptions.GranularApprovalPolicy)
.approvalsReviewer(ApprovalsReviewer)
.clientUserMessageId(String)
.personality(Personality)
.serviceTier(String)
.reasoningSummary(ReasoningSummary)
.outputSchema(JsonNode)
.build()
```

## 6. CodexTurn

```java
String id()
Flow.Publisher<CodexEvent> events()
CompletableFuture<TurnResult> resultAsync()
TurnResult await()
JsonNode steer(String prompt)
void interrupt()
```

事件是单消费流程。要同时接收事件和最终结果，先订阅 `events()`，再调用 `resultAsync()` 或 `await()`。

`CancellationToken.cancel()` 会向绑定的运行中 Turn 发送 `turn/interrupt`。已经取消的
Token 不能用于启动新 Turn。

## 7. UserInput

静态工厂：

```java
UserInput.text(String text)
UserInput.image(String url)
UserInput.localImage(Path path)
UserInput.skill(String name, Path path)
UserInput.mention(String name, Path path)
```

## 8. CodexEvent

```java
public record CodexEvent(String method, JsonNode params) {
    public CodexEventType type();
    public CodexNotification notification();
}
```

访问：

```java
event.method()
event.params()
event.type()   // CodexEventType；未知方法返回 UNKNOWN
event.notification() // 已知通知的 sealed 强类型视图
```

`CodexNotification` 为 sealed interface，当前覆盖 Turn、Item、流式 delta、Usage、Thread 和
MCP 生命周期，以及 Goal、Skills、计划、diff、配置诊断等高频通知。未知方法返回
`CodexNotification.Unknown`，原始参数仍可通过
`raw()` 获取。

公共通知契约为：

```java
CodexEventType type();
String method();       // 已知通知由 type().method() 统一提供
JsonNode raw();        // 保留完整原始参数和未来新增字段
```

`Delta` 除通用的 `threadId`、`turnId`、`itemId` 和 `delta` 外，还保留推理事件对应的
`contentIndex` 与 `summaryIndex`。`Error.error()` 返回强类型 `TurnError`，不再要求调用方解析
错误节点。文件更新的 `kind()` 返回 sealed `CodexItem.PatchChangeKind`，可区分 `Add`、
`Delete`、带可选 `movePath` 的 `Update`，以及保留未知操作的 `Unknown`。

已知枚举值：

```java
TURN_STARTED
TURN_COMPLETED
ITEM_STARTED
ITEM_COMPLETED
AGENT_MESSAGE_DELTA
REASONING_TEXT_DELTA
REASONING_SUMMARY_TEXT_DELTA
REASONING_SUMMARY_PART_ADDED
COMMAND_OUTPUT_DELTA
TERMINAL_INTERACTION
FILE_CHANGE_PATCH_UPDATED
MCP_TOOL_CALL_PROGRESS
MCP_SERVER_STATUS_UPDATED
MCP_SERVER_OAUTH_LOGIN_COMPLETED
PLAN_DELTA
TOKEN_USAGE_UPDATED
THREAD_STARTED
THREAD_STATUS_CHANGED
THREAD_ARCHIVED
THREAD_UNARCHIVED
THREAD_DELETED
THREAD_CLOSED
THREAD_REVERTED
THREAD_NAME_UPDATED
THREAD_GOAL_UPDATED
THREAD_GOAL_CLEARED
SKILLS_CHANGED
TURN_DIFF_UPDATED
TURN_PLAN_UPDATED
SERVER_REQUEST_RESOLVED
CONTEXT_COMPACTED
WARNING
CONFIG_WARNING
DEPRECATION_NOTICE
MODEL_REROUTED
ERROR
UNKNOWN
```

`CodexEventType.fromMethod(String)` 可把协议方法转换为枚举，`method()` 返回对应的原始
方法名；`UNKNOWN.method()` 为 `null`。

## 9. Goal、Skills 与 Config

```java
ThreadGoal ThreadGoals.set(GoalUpdate update)
Optional<ThreadGoal> ThreadGoals.get()
boolean ThreadGoals.clear()

SkillsListResult SkillsClient.list()
SkillsListResult SkillsClient.list(SkillsListOptions options)
void SkillsClient.setExtraRoots(List<Path> roots)
boolean SkillsClient.enableByName(String name)
boolean SkillsClient.disableByName(String name)
boolean SkillsClient.enable(Path path)
boolean SkillsClient.disable(Path path)

ConfigSnapshot ConfigClient.read()
ConfigSnapshot ConfigClient.read(ConfigReadOptions options)
ConfigWriteResult ConfigClient.write(ConfigWriteRequest request)
ConfigWriteResult ConfigClient.batchWrite(ConfigBatchWriteRequest request)
ConfigRequirements ConfigClient.requirements()
```

`ConfigSnapshot.config()` 和 `ConfigRequirements.raw()` 保留开放的 JSON 配置主体；配置层、
来源、版本、写入状态和常见限制提供强类型访问。详见第 17 章。

## 10. 工具审批与生命周期扩展

```java
ApprovalHandler.requestApproval(ApprovalRequest request)

ToolInterceptor.beforeToolCall(ApprovalRequest request)
ToolInterceptor.afterToolCall(ToolCallResult result)

ToolObserver.onApprovalRequested(ApprovalRequest request)
ToolObserver.onStarted(ToolCallContext context)
ToolObserver.onOutput(ToolCallContext context, String delta)
ToolObserver.onCompleted(ToolCallResult result)
```

注册：

```java
CodexClientConfig.builder()
        // 仅展示 API；生产环境应使用业务策略或异步人工审批。
        .approvalHandler(ApprovalHandler.acceptAll())
        .toolInterceptor(interceptor)
        .toolObserver(observer)
        .build();
```

`ApprovalRequest.Decision` 包含 `ACCEPT`、`ACCEPT_FOR_SESSION`、`DECLINE` 和
`CANCEL`。`ToolCallContext.Kind` 包含 `COMMAND`、`FILE_CHANGE`、`MCP`、
`WEB_SEARCH` 和 `UNKNOWN`。

### ApprovalRequest

```java
public sealed interface ApprovalRequest {
    ToolCallContext context();
    String reason();
    JsonNode raw();
}

record ApprovalRequest.Command(
        ToolCallContext context,
        String reason,
        JsonNode raw) implements ApprovalRequest

record ApprovalRequest.FileChange(
        ToolCallContext context,
        String reason,
        String grantRoot,
        JsonNode raw) implements ApprovalRequest
```

决策及其 JSON-RPC 协议值：

| Java 枚举 | 协议值 |
|---|---|
| `ACCEPT` | `accept` |
| `ACCEPT_FOR_SESSION` | `acceptForSession` |
| `DECLINE` | `decline` |
| `CANCEL` | `cancel` |

```java
String value = decision.wireValue();
ApprovalRequest.Decision decision = ApprovalRequest.Decision.fromWireValue(value);
```

`fromWireValue` 对未知值返回 `DECLINE`，适合失败关闭；对外 HTTP API 通常应先显式
校验字符串，以便向客户端返回 400，而不是把拼写错误静默解释为拒绝。

### ToolCallContext 与 ToolCallResult

```java
public record ToolCallContext(
        String threadId,
        String turnId,
        String itemId,
        ToolCallContext.Kind kind,
        String toolName,
        String command,
        String workingDirectory,
        JsonNode raw) {}

public record ToolCallResult(
        ToolCallContext context,
        boolean successful,
        String error,
        JsonNode item) {}
```

字段可能因工具类型或 CLI 版本而为 `null`，前端 DTO 不应直接复用这两个含原始
`JsonNode` 的类型。`successful` 在 `error != null`、Item 状态为 `failed` 或
`declined` 时为 `false`。

### ToolInterceptor 返回值

```java
new ToolInterceptor.BeforeResult.Continue()
new ToolInterceptor.BeforeResult.Decide(ApprovalRequest.Decision.DECLINE)
```

前置拦截器按注册顺序执行，`Decide` 会短路后续审批；完成回调按相反顺序执行。
`ApprovalHandler` 可以异步返回未完成的 `CompletionStage`，用于等待 Web 用户审批。

## 11. TurnResult

```java
public record TurnResult(
        String id,
        String status,
        String finalResponse,
        List<JsonNode> items,
        JsonNode usage,
        JsonNode turn) {}
```

访问：

```java
result.id()
result.status()
result.finalResponse()
result.items()
result.usage()
result.turn()
result.typedItems() // List<CodexItem>
result.typedUsage() // TokenUsage；没有 Usage 通知时为 null
result.typedTurn()  // generated.v2.Turn
```

`CodexItem` 按 app-server v2 的全部 Item 判别值提供 sealed record；未来新增的类型会
降级为 `CodexItem.Unknown`。Agent 最终答复阶段对应
`CodexItem.MessagePhase.FINAL_ANSWER`，协议值为 `final_answer`。

## 12. CodexException

```java
public class CodexException extends RuntimeException
```

构造器：

```java
CodexException(String message)
CodexException(String message, Throwable cause)
```

## 13. JsonRpcException

```java
public class JsonRpcException extends CodexException
```

访问 app-server 错误详情：

```java
int code()
JsonNode data()
String rpcMessage()
String getMessage()
```

标准 JSON-RPC 错误映射为 `JsonRpcParseException`、`InvalidRequestException`、
`MethodNotFoundException`、`InvalidParamsException` 和 `InternalRpcException`。
过载映射为 `ServerBusyException`，服务端内部重试耗尽映射为
`RetryLimitExceededException`。

## 14. 完整组合示例

```java
import io.github.majiajustar.codex.*;
import io.github.majiajustar.codex.event.*;
import io.github.majiajustar.codex.thread.*;
import io.github.majiajustar.codex.tool.*;
import io.github.majiajustar.codex.turn.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Flow;

public final class FullExample {
    public static void main(String[] args) {
        CodexClientConfig config = CodexClientConfig.builder()
                .requestTimeout(Duration.ofSeconds(90))
                .clientInfo("full_example", "Full Example", "1.0.0")
                // 仅适用于受信任的本地示例；生产环境应接入策略或人工审批。
                .approvalHandler(ApprovalHandler.acceptAll())
                .build();

        try (CodexClient codex = CodexClient.create(config)) {
            CodexThread thread = codex.startThread(ThreadOptions.builder()
                    .workingDirectory(Path.of("D:/code/my-project"))
                    .sandbox(ThreadOptions.Sandbox.WORKSPACE_WRITE)
                    .approvalPolicy(ThreadOptions.ApprovalPolicy.ON_REQUEST)
                    .build());

            CodexTurn turn = thread.startTurn(
                    List.of(UserInput.text("运行相关测试并修复失败。")),
                    TurnOptions.builder().reasoningEffort("high").build());

            turn.events().subscribe(new Flow.Subscriber<>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    subscription.request(1);
                }

                @Override
                public void onNext(CodexEvent event) {
                    System.out.println(event.method());
                    subscription.request(1);
                }

                @Override
                public void onError(Throwable error) {
                    error.printStackTrace();
                }

                @Override
                public void onComplete() {
                    System.out.println("event stream completed");
                }
            });

            TurnResult result = turn.await();
            System.out.println(result.finalResponse());
        }
    }
}
```

---

[上一章](12-production-practices.md) · [返回目录](README.md) · [下一章：Solon SSE 完整案例](14-solon-sse-complete-example.md)
