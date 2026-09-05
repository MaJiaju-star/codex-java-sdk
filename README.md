# 非官方 Codex Java SDK

这是一个面向 Java 25 的 Codex SDK，用于在 JVM 应用中嵌入 Codex Agent。SDK 会启动
`codex app-server`，完成 JSON-RPC v2 握手，并通过标准输入输出交换 JSONL 消息。

> [!IMPORTANT]
> 这是一个由社区维护的非官方 SDK，与 OpenAI 没有隶属关系，也未获得 OpenAI 的认可或维护。

完整使用教程：[中文教程](docs/README.md)。

Solon Web SSE 完整示例：[多会话流式对话案例](examples/solon-sse-chat/README.md)。

## 环境要求

- JDK 25
- Maven 3.9+
- `PATH` 中存在 `codex` 可执行文件，或者显式指定 Codex 可执行文件路径

## Maven 坐标

```xml
<dependency>
  <groupId>io.github.majiajustar</groupId>
  <artifactId>codex-java-sdk</artifactId>
  <version>0.0.4-SNAPSHOT</version>
</dependency>
```

当前版本尚未发布到 Maven Central，可先在 SDK 根目录执行 `mvn install` 安装到本地 Maven 仓库。

## 构建

```shell
mvn test
```

SDK 提交了从 app-server v2 Schema 生成的 Java record 和 enum。生成或检查协议类型：

```shell
mvn -Pgenerate-protocol generate-sources
mvn -Pcheck-protocol validate
```

当前生成快照以 Codex `v0.153.0` 的 app-server v2 Schema 为基线。检查其他源码目录时，
通过 `CODEX_REPO_ROOT` 显式指定对应的 Codex 仓库根目录。

## 快速开始

```java
import io.github.majiajustar.codex.CodexClient;
import io.github.majiajustar.codex.CodexThread;
import io.github.majiajustar.codex.thread.ThreadOptions;
import io.github.majiajustar.codex.turn.TurnResult;
import java.nio.file.Path;

try (CodexClient codex = CodexClient.create()) {
    CodexThread thread = codex.startThread(ThreadOptions.builder()
            .workingDirectory(Path.of("/path/to/project"))
            .sandbox(ThreadOptions.Sandbox.WORKSPACE_WRITE)
            .build());

    TurnResult result = thread.run("请用三个要点解释这个仓库。");
    System.out.println(result.finalResponse());
}
```

## 包结构

根包只保留客户端、配置和运行时句柄，其他 API 按领域划分：

```text
io.github.majiajustar.codex            客户端、配置、Thread/Turn 运行时句柄
io.github.majiajustar.codex.thread     Thread 配置和列表选项
io.github.majiajustar.codex.turn       Turn 配置、输入、结果和 Usage
io.github.majiajustar.codex.event      事件、通知和 Item
io.github.majiajustar.codex.goal       Thread 长期目标、状态和用量
io.github.majiajustar.codex.skills     Skill 发现、依赖和启停
io.github.majiajustar.codex.config     分层配置读写与管理员约束
io.github.majiajustar.codex.mcp        MCP Server 注册、运行时管理、资源与工具调用
io.github.majiajustar.codex.model      模型目录类型
io.github.majiajustar.codex.tool       审批、拦截器和工具生命周期
io.github.majiajustar.codex.sandbox    沙箱策略
io.github.majiajustar.codex.exception  SDK 和 JSON-RPC 异常
io.github.majiajustar.codex.generated  自动生成的 app-server 协议类型
io.github.majiajustar.codex.internal   内部实现，不属于公共 API
```

当 `codex` 不在 `PATH` 中时，可以指定可执行文件：

```java
CodexClientConfig config = CodexClientConfig.builder()
        .codexExecutable(Path.of("/path/to/codex"))
        .build();

try (CodexClient codex = CodexClient.create(config)) {
    // ...
}
```

## 内网 API Key 配置

使用内部 OpenAI-compatible 接口时，建议注册独立的强类型 Provider。SDK 会自动生成
`env_key`、`wire_api = "responses"` 和 `requires_openai_auth = false`，避免模型请求误用本机已有的
ChatGPT 登录态：

```java
import io.github.majiajustar.codex.model.OpenAiCompatibleProviderConfig;
import java.net.URI;

OpenAiCompatibleProviderConfig internal = OpenAiCompatibleProviderConfig.builder("internal")
        .name("Internal Codex")
        .baseUrl(URI.create("http://codex-api.internal/v1"))
        .build();

CodexClientConfig config = CodexClientConfig.builder()
        .openAiCompatibleProvider(internal)
        .apiKey(System.getenv("INTERNAL_CODEX_API_KEY"))
        .model("gpt-internal")
        .modelReasoningEffort(CodexClientConfig.ReasoningEffort.HIGH)
        .webSearch(CodexClientConfig.WebSearchMode.DISABLED)
        .approvalPolicy(ThreadOptions.ApprovalPolicy.NEVER)
        .workspaceNetworkAccess(false)
        .build();
```

`apiKey(...)` 只会通过 `OPENAI_API_KEY` 环境变量传递给 app-server 子进程，不会出现在命令行中。
`openAiCompatibleProvider(...)` 会自动选中注册的 Provider，生成的强类型配置会覆盖相同的原始
`configOverride(...)` 配置项。

若内网规定使用其他环境变量名，可以显式配置：

```java
OpenAiCompatibleProviderConfig internal = OpenAiCompatibleProviderConfig.builder("internal")
        .baseUrl(URI.create("http://codex-api.internal/v1"))
        .apiKeyEnvironmentVariable("INTERNAL_CODEX_API_KEY")
        .build();

CodexClientConfig config = CodexClientConfig.builder()
        .environment("INTERNAL_CODEX_API_KEY", System.getenv("INTERNAL_CODEX_API_KEY"))
        .openAiCompatibleProvider(internal)
        .build();
```

## 注册 MCP Server

MCP Server 在创建 `CodexClient` 时通过强类型配置注册，不会修改用户的全局
`~/.codex/config.toml`。stdio 示例：

```java
McpServerConfig filesystem = McpServerConfig.stdio("npx")
        .args("-y", "@modelcontextprotocol/server-filesystem", "D:/workspace")
        .env("LOG_LEVEL", "info")
        .envVar(McpEnvVar.inherit("HOME"))
        .cwd(Path.of("D:/workspace"))
        .enabled(true)
        .required(true)
        .startupTimeout(Duration.ofSeconds(10))
        .toolTimeout(Duration.ofSeconds(60))
        .supportsParallelToolCalls(true)
        .enabledTools("read_file", "write_file")
        .defaultToolsApprovalMode(McpToolApprovalMode.PROMPT)
        .tool("read_file", McpToolConfig.approval(McpToolApprovalMode.APPROVE))
        .build();

CodexClientConfig config = CodexClientConfig.builder()
        .mcpServer("filesystem", filesystem)
        .build();
```

Streamable HTTP 使用 `McpServerConfig.streamableHttp(URI)` 创建，并支持认证头、环境变量认证头及
OAuth 配置。SDK 不提供明文 bearer token 配置；`bearerTokenEnvVar(...)` 只把环境变量名称写入
Codex 配置，实际密钥通过子进程环境传递。完整示例、字段和安全说明见
[配置教程](docs/03-configuration.md)与[审批、安全和沙箱](docs/08-approvals-and-security.md)。

创建 Client 后，可通过 `codex.mcp()` 使用 v0.153 app-server 的 MCP 运行时 API：

```java
McpServerStatusPage statuses = codex.mcp().listStatuses();
McpResourceReadResult resource = codex.mcp().readResource(
        McpResourceReadRequest.create("docs", URI.create("docs://guide")));
McpToolCallResponse response = codex.mcp().callTool(
        McpToolCallRequest.create(thread.id(), "docs", "search", arguments));
```

还支持重新加载配置以及启动 OAuth 登录。完整说明见
[MCP 运行时 API](docs/16-mcp-runtime.md)。

## Goal、Skills 与运行时配置

Thread Goal 适合长时间运行、带预算或可暂停的 Agent 任务：

```java
ThreadGoal goal = thread.goals().set(GoalUpdate.builder()
        .objective("修复项目并通过全部测试")
        .tokenBudget(200_000)
        .build());
```

Skills 和 Config 是连接级 API：

```java
SkillsListResult skills = codex.skills().list(
        new SkillsListOptions(List.of(Path.of("D:/workspace/project")), false));

ConfigSnapshot config = codex.config().read(
        new ConfigReadOptions(Path.of("D:/workspace/project"), true));

ConfigWriteResult updated = codex.config().write(
        ConfigWriteRequest.replace("web_search", "cached"));
```

配置写入支持 `expectedVersion` 乐观并发控制和批量修改；管理员限制可通过
`codex.config().requirements()` 查询。完整说明见
[Goal、Skills 与 Config API](docs/17-goals-skills-config.md)。

## 流式事件

Turn 事件使用 JDK `Flow` API。建议在等待结果前完成订阅；在 `turn/start` 返回前已经收到的事件，
也会为订阅者保留并重放。

```java
CodexTurn turn = thread.startTurn("运行测试并解释所有失败。");
turn.events().subscribe(new Flow.Subscriber<>() {
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }
    public void onNext(CodexEvent event) {
        System.out.println(event.type() + ": " + event.params());
    }
    public void onError(Throwable error) { error.printStackTrace(); }
    public void onComplete() {}
});

TurnResult result = turn.await();
```

已知通知和已完成 Item 提供 sealed 强类型视图，同时保留原始 JSON 以支持协议向前兼容：

```java
CodexNotification notification = event.notification();
if (notification instanceof CodexNotification.ItemCompleted completed
        && completed.item() instanceof CodexItem.AgentMessage message
        && message.phase() == CodexItem.MessagePhase.FINAL_ANSWER) {
    System.out.println(message.text());
}

System.out.println(notification.type());   // CodexEventType
System.out.println(notification.method()); // 准确的 JSON-RPC method

TokenUsage usage = result.typedUsage();
List<CodexItem> items = result.typedItems();
CodexModelList models = codex.listModels();
```

无法识别的通知方法和未来新增的 Item 类型会转换为 `CodexNotification.Unknown` 和
`CodexItem.Unknown`，而不是导致反序列化失败。原始载荷可以通过 `raw()` 获取。
已知通知的 `method()` 由 `CodexEventType` 统一维护；`raw()` 则始终保留完整参数，供新版
协议字段透传和诊断使用。

## 取消、追加指令与多目录

`CodexTurn` 支持 `steer(...)`、`interrupt()` 和 `resultAsync()`。可以使用
`CancellationToken` 实现类似 AbortSignal 的协作式取消：

```java
CancellationToken cancellation = new CancellationToken();
CodexTurn turn = thread.startTurn("运行测试套件", cancellation);

// 在另一个请求、超时处理器或 UI 回调中调用：
cancellation.cancel();
```

通过 app-server v2 的 runtime-workspace-roots 字段添加额外项目目录：

```java
ThreadOptions options = ThreadOptions.builder()
        .workingDirectory(Path.of("/workspace/project"))
        .additionalDirectory(Path.of("/workspace/shared"))
        .skipGitRepoCheck(true)
        .build();
```

额外目录必须使用绝对路径。`skipGitRepoCheck` 用于与其他 SDK 的配置能力保持一致；app-server
不会执行 `codex exec` 的 Git 预检查，因此不需要传输对应的协议字段。

`CodexClient.request(...)` 提供原始 JSON-RPC 调用入口，可以访问尚未提供便捷封装的
app-server API。

Thread 列表通过 `ThreadListOptions` 和 `ThreadListResponse` 提供强类型游标分页；归档和取消归档
同时提供客户端和 Thread 方法。服务过载响应会按照可配置的指数退避策略自动重试，其他不可重试的
JSON-RPC 错误则会映射为对应的异常类型。

## 审批处理

默认审批处理器会接受命令执行和文件修改请求。生产应用应显式配置 `approvalHandler(...)`，
也可以注册强类型的 `ToolInterceptor` 和 `ToolObserver` 扩展。原始的
`serverRequestHandler(...)` 仍可用于处理自定义或未来新增的服务端请求。

## 许可证

本项目使用 [Apache License 2.0](LICENSE)。
