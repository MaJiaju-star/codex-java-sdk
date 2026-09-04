# 03. 客户端、线程与 Turn 配置

[上一章](02-quickstart.md) · [返回目录](README.md) · [下一章：Thread 生命周期](04-thread-lifecycle.md)

SDK 有三个配置层级：

| 层级 | 类型 | 生效范围 |
|---|---|---|
| 进程/连接 | `CodexClientConfig` | app-server 子进程和整条连接 |
| 会话 | `ThreadOptions` | 新建或恢复的 Thread 及后续 Turn |
| 单次执行 | `TurnOptions` | 当前 Turn，并可能成为后续 Turn 的有效设置 |

## 1. CodexClientConfig

内网 OpenAI-compatible endpoint 应注册成独立的强类型 Provider：

```java
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

`openAiCompatibleProvider(...)` 会注册并选中 Provider，等价于以下 Codex 配置：

```toml
model_provider = "internal"

[model_providers.internal]
name = "Internal Codex"
base_url = "http://codex-api.internal/v1"
env_key = "OPENAI_API_KEY"
wire_api = "responses"
requires_openai_auth = false
```

`apiKey(...)` 只通过 app-server 子进程的 `OPENAI_API_KEY` 环境变量传递，不会出现在
命令行参数中。Provider 的 `env_key` 会让模型请求明确读取该变量；
`requires_openai_auth = false` 会阻止这个 Provider 使用本机 `auth.json` 中的 ChatGPT
登录凭据。因此，即使机器已经通过 Codex CLI 登录，该 Provider 的模型请求仍使用内网 API Key。

不要只组合 `.baseUrl(...)` 和 `.apiKey(...)` 来隔离认证：`baseUrl` 仅覆盖内置 OpenAI
Provider 的 `openai_base_url`，不会改变它选择认证来源的规则。若继续使用内置 Provider，
app-server 仍可能采用本机持久化登录态。

内网使用自定义环境变量名时，让 Provider 引用相同变量：

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

### 多用户 API Key 与工作区隔离

API Key 保存在 app-server 子进程环境中，因此它的作用域是整个 `CodexClient`，不是单个
Thread 或 Turn。多个用户即使使用不同的 `ThreadOptions.workingDirectory(...)`，也不能安全地
共享一个 `CodexClient`。推荐让每个用户拥有独立的 Client、`CODEX_HOME`、工作区和 API Key：

```java
Path userWorkspace = workspaceRoot.resolve(userId);
Path userCodexHome = codexHomeRoot.resolve(userId);

OpenAiCompatibleProviderConfig internal = OpenAiCompatibleProviderConfig.builder("internal")
        .name("Internal Codex")
        .baseUrl(URI.create("https://codex-api.internal/v1"))
        .build();

CodexClientConfig config = CodexClientConfig.builder()
        .workingDirectory(userWorkspace)
        .environment("CODEX_HOME", userCodexHome.toString())
        .openAiCompatibleProvider(internal)
        .apiKey(userApiKey)
        .configOverride("shell_environment_policy.filters.OPENAI_API_KEY=\"exclude\"")
        .build();

CodexClient client = CodexClient.create(config);
CodexThread thread = client.startThread(ThreadOptions.builder()
        .workingDirectory(userWorkspace)
        .sandbox(ThreadOptions.Sandbox.WORKSPACE_WRITE)
        .build());
```

隔离要求：

| 资源 | 推荐作用域 | 原因 |
|---|---|---|
| `CodexClient` / app-server | 每个用户一个 | 子进程环境中的 API Key 是进程级状态 |
| API Key | 每个用户一个 | 计费、配额、审计和撤销互不影响 |
| `CODEX_HOME` | 每个用户一个 | 隔离 Thread 历史、配置、缓存和其他本地状态 |
| 工作区 | 每个用户一个 | 防止文件、Git 状态和命令执行相互影响 |
| Thread | 用户内部按任务创建 | Thread 不是跨用户的安全边界 |

同一用户可以在自己的 Client 中创建多个 Thread。API Key 轮换时，应关闭并重新创建该用户的
Client。用户退出或长期空闲时也应关闭 Client，避免 app-server 进程无限增长。

`apiKey(...)` 把密钥放入 app-server 环境，是为了让模型 Provider 读取它；但 Codex 执行的
Shell 命令也可能继承父进程环境。上例显式排除了 `OPENAI_API_KEY`，模型请求仍能使用该密钥，
工具启动的命令则看不到它。使用自定义密钥变量名时，应把过滤规则中的名称同步替换。

不要记录 `CodexClientConfig` 或调用其 `toString()`：当前配置对象包含 `environment` Map，可能
暴露密钥。用户 ID 和工作区路径必须由可信后端映射，不能直接采用前端提交的任意路径。

常用全局配置均有具名 API：

| Builder 方法 | Codex 配置键 |
|---|---|
| `baseUrl(String)` | `openai_base_url` |
| `openAiCompatibleProvider(OpenAiCompatibleProviderConfig)` | `model_providers.<id>`，并选中 `model_provider` |
| `modelProvider(String)` | `model_provider` |
| `model(String)` | `model` |
| `modelReasoningEffort(ReasoningEffort)` | `model_reasoning_effort` |
| `webSearch(WebSearchMode)` | `web_search` |
| `approvalPolicy(ApprovalPolicy)` | `approval_policy` |
| `workspaceNetworkAccess(boolean)` | `sandbox_workspace_write.network_access` |
| `mcpServer(String, McpServerConfig)` | `mcp_servers.<name>` |

具名 API 会排在原始 `configOverride(s)` 后面，因此相同配置键以具名 API 为准。
`openAiCompatibleProvider(...)` 会把当前默认 `modelProvider` 设置成其 ID；如果随后调用
`modelProvider(...)`，可以改选已经配置的其他 Provider。未提供具名方法的高级配置仍可通过
`configOverride("key=value")` 传递。

完整示例：

```java
CodexClientConfig config = CodexClientConfig.builder()
        .codexExecutable(Path.of("D:/tools/codex.exe"))
        .workingDirectory(Path.of("D:/services/codex-host"))
        .environment("RUST_LOG", "warn")
        .clientInfo("my_java_service", "My Java Service", "1.4.0")
        .experimentalApi(true)
        .requestTimeout(Duration.ofSeconds(90))
        .retryPolicy(RetryPolicy.overloadDefaults())
        .approvalHandler(ApprovalHandler.declineAll())
        .build();
```

工具拦截器、观察器和交互式审批的完整写法见第八章。尚未强类型化的双向请求
仍可使用 `serverRequestHandler(...)`。

### 1.1 command

完全覆盖子进程命令：

```java
.command(List.of("docker", "exec", "codex-container", "codex", "app-server"))
```

这个接口适合包装脚本、容器入口和测试模拟服务器。命令必须保持 app-server 的 stdin/stdout JSONL 协议。

### 1.2 codexExecutable

只指定 Codex 可执行文件，SDK 自动追加 `app-server`：

```java
.codexExecutable(Path.of("/opt/codex/bin/codex"))
```

不要同时依赖 `command(...)` 和 `codexExecutable(...)` 的调用顺序；Builder 最后一次设置的命令配置生效。

### 1.3 workingDirectory

```java
.workingDirectory(Path.of("D:/services/codex-host"))
```

这是 app-server 子进程自身的启动目录，不等同于 Codex 操作项目时使用的 Thread `cwd`。一般应使用 `ThreadOptions.workingDirectory(...)` 指定项目目录。

### 1.4 environment

```java
.environment("RUST_LOG", "codex_app_server=info")
.environment("MY_INTERNAL_TRACE_ID", traceId)
```

配置会覆盖或补充 Java 进程继承给子进程的环境变量。不要传入 `null`；底层 `ProcessBuilder.environment()` 不接受空值。

### 1.5 clientInfo

```java
.clientInfo("acme_ci_agent", "ACME CI Agent", "2.3.1")
```

该信息随 `initialize` 发送，用于识别集成方。`name` 应稳定、机器可读；`title` 面向人类；`version` 应对应应用版本。

### 1.6 experimentalApi

```java
.experimentalApi(true)
```

默认为 `true`。关闭后，要求实验能力的原始 JSON-RPC 方法可能返回错误。仅使用核心 Thread/Turn API 的保守应用可以设为 `false`。

### 1.7 requestTimeout

```java
.requestTimeout(Duration.ofMinutes(2))
```

它控制 SDK 同步请求等待 JSON-RPC 响应的最长时间。它不是 Turn 总执行时间限制：`turn/start` 很快返回，真正执行通过事件持续到 `turn/completed`。

对于公开的 `requestAsync(...)`，调用方应自行附加异步超时：

```java
CompletableFuture<JsonNode> future = codex.requestAsync("thread/list", mapper.createObjectNode())
        .orTimeout(30, TimeUnit.SECONDS);
```

### 1.8 retryPolicy

`RetryPolicy.overloadDefaults()` 是默认值，只对服务端明确报告的入口过载做指数退避
重试。使用 `RetryPolicy.disabled()` 可关闭，完整说明见第 11 章。

### 1.9 MCP Server

`mcpServer(name, config)` 将强类型配置转换为 app-server 启动参数。它只对当前
`CodexClient` 子进程生效，不会写入全局 `config.toml`。同名 Server 注册多次时，最后一次生效。
由于 Codex CLI dotted override 的限制，名称只能包含字母、数字、下划线和连字符。

stdio MCP Server：

```java
McpServerConfig server = McpServerConfig.stdio("npx")
        .args("-y", "@modelcontextprotocol/server-filesystem", "D:/workspace")
        .env("LOG_LEVEL", "info")
        .envVar(McpEnvVar.inherit("HOME"))
        .envVar(McpEnvVar.remote("REMOTE_TOKEN"))
        .cwd(Path.of("D:/workspace"))
        .enabled(true)
        .required(true)
        .startupTimeout(Duration.ofSeconds(10))
        .toolTimeout(Duration.ofSeconds(60))
        .supportsParallelToolCalls(true)
        .enabledTools("read_file", "write_file")
        .disabledTools("delete_file")
        .defaultToolsApprovalMode(McpToolApprovalMode.PROMPT)
        .tool("read_file", McpToolConfig.approval(McpToolApprovalMode.APPROVE))
        .tool("write_file", McpToolConfig.approval(McpToolApprovalMode.WRITES))
        .build();

CodexClientConfig config = CodexClientConfig.builder()
        .mcpServer("filesystem", server)
        .build();
```

Streamable HTTP MCP Server：

```java
McpServerConfig server = McpServerConfig.streamableHttp(
                URI.create("https://mcp.internal.example.com/mcp"))
        .bearerTokenEnvVar("MCP_TOKEN")
        .httpHeader("X-Tenant", "internal")
        .envHttpHeader("Authorization", "MCP_AUTH_HEADER")
        .httpHeadersHelper("resolve-mcp-headers")
        .auth(McpAuthMode.OAUTH)
        .oauth(McpOAuthConfig.builder()
                .clientId("internal-client")
                .callbackUrl(URI.create("http://127.0.0.1:8765/callback"))
                .callbackPort(8765)
                .build())
        .environmentId("local")
        .omitToolsFrom(McpToolExposureSurface.CODE_MODE)
        .scopes("files.read", "files.write")
        .oauthResource("https://mcp.internal.example.com")
        .enabled(true)
        .build();

CodexClientConfig config = CodexClientConfig.builder()
        .environment("MCP_TOKEN", System.getenv("MCP_TOKEN"))
        .environment("MCP_AUTH_HEADER", System.getenv("MCP_AUTH_HEADER"))
        .mcpServer("internal", server)
        .build();
```

字段映射：

| Builder 方法 | Codex 配置键 | 传输 |
|---|---|---|
| `args(...)` | `args` | stdio |
| `env(name, value)` | `env` | stdio |
| `envVar(...)` | `env_vars` | stdio |
| `cwd(Path)` | `cwd` | stdio |
| `bearerTokenEnvVar(String)` | `bearer_token_env_var` | HTTP |
| `httpHeader(name, value)` | `http_headers` | HTTP |
| `envHttpHeader(name, envName)` | `env_http_headers` | HTTP |
| `httpHeadersHelper(command)` | `http_headers_helper` | HTTP |
| `auth(McpAuthMode)` | `auth` | HTTP |
| `oauth(McpOAuthConfig)` | `oauth` | HTTP |
| `environmentId(String)` | `environment_id` | 两者 |
| `omitToolsFrom(...)` | `omit_tools_from` | 两者 |
| `scopes(...)` | `scopes` | 两者 |
| `oauthResource(String)` | `oauth_resource` | 两者 |
| `enabled(boolean)` | `enabled` | 两者 |
| `required(boolean)` | `required` | 两者 |
| `startupTimeout(Duration)` | `startup_timeout_sec` | 两者 |
| `toolTimeout(Duration)` | `tool_timeout_sec` | 两者 |
| `supportsParallelToolCalls(boolean)` | `supports_parallel_tool_calls` | 两者 |
| `enabledTools(...)` | `enabled_tools` | 两者 |
| `disabledTools(...)` | `disabled_tools` | 两者 |
| `defaultToolsApprovalMode(...)` | `default_tools_approval_mode` | 两者 |
| `tool(name, config)` | `tools.<tool>.approval_mode` | 两者 |

`enabledTools()` 的空参数形式会显式写入空白名单，即不暴露任何工具；从未调用该方法则不设置
白名单。工具审批模式包括 `AUTO`、`PROMPT`、`WRITES` 和 `APPROVE`。

## 2. ThreadOptions

```java
ThreadOptions options = ThreadOptions.builder()
        .model("gpt-5.5")
        .workingDirectory(Path.of("D:/code/my-project"))
        .sandbox(ThreadOptions.Sandbox.WORKSPACE_WRITE)
        .approvalPolicy(ThreadOptions.ApprovalPolicy.ON_REQUEST)
        .approvalsReviewer(ApprovalsReviewer.USER)
        .baseInstructions("你是代码维护代理。")
        .ephemeral(false)
        .developerInstructions("修改前先运行相关测试；保持现有代码风格。")
        .modelProvider("openai")
        .personality(Personality.PRAGMATIC)
        .serviceName("my-java-service")
        .serviceTier("priority")
        .threadSource("java-sdk")
        .build();
```

| Builder 方法 | 说明 |
|---|---|
| `model(String)` | 指定模型标识；是否可用取决于当前账户和 CLI |
| `workingDirectory(Path)` | Thread 的项目工作目录 |
| `sandbox(Sandbox)` | 文件和命令执行权限范围 |
| `approvalPolicy(ApprovalPolicy)` | 哪些操作需要审批 |
| `ephemeral(boolean)` | 是否创建仅内存存在的临时 Thread |
| `developerInstructions(String)` | 追加给代理的开发者指令 |
| `approvalsReviewer(ApprovalsReviewer)` | 审批交给用户或自动审查器 |
| `baseInstructions(String)` | 覆盖基础代理指令 |
| `config(JsonNode)` | 注入本 Thread 使用的 config.toml 等价配置 |
| `modelProvider(String)` | 模型供应商标识 |
| `personality(Personality)` | `NONE`、`FRIENDLY` 或 `PRAGMATIC` |
| `serviceName(String)` | 启动 Thread 的服务名称 |
| `serviceTier(String)` | 服务等级，例如 `priority` |
| `sessionStartSource(ThreadStartSource)` | `STARTUP` 或 `CLEAR` |
| `threadSource(String)` | 集成方自定义分析来源 |
| `additionalDirectory(Path)` | 添加绝对运行时工作区根目录，等价于 exec `--add-dir` |
| `additionalDirectories(List<Path>)` | 一次添加多个绝对运行时工作区根目录 |
| `skipGitRepoCheck(boolean)` | exec 配置兼容项；app-server 不执行该 Git 预检 |
| `granularApprovalPolicy(...)` | 细粒度审批开关，和粗粒度策略互斥 |
| `lastTurnId(String)` | 分叉时使用的历史边界 |

### 2.1 Sandbox

```java
ThreadOptions.Sandbox.READ_ONLY
ThreadOptions.Sandbox.WORKSPACE_WRITE
ThreadOptions.Sandbox.DANGER_FULL_ACCESS
```

- `READ_ONLY`：适合代码阅读、解释和审查；
- `WORKSPACE_WRITE`：允许在工作区中修改文件，适合开发任务；
- `DANGER_FULL_ACCESS`：完全访问，风险最高。

### 2.2 ApprovalPolicy

```java
ThreadOptions.ApprovalPolicy.UNTRUSTED
ThreadOptions.ApprovalPolicy.ON_REQUEST
ThreadOptions.ApprovalPolicy.NEVER
```

- `UNTRUSTED`：更严格地要求批准不受信操作；
- `ON_REQUEST`：代理在需要提升权限时请求审批；
- `NEVER`：不请求交互审批，但操作仍受沙箱限制，无法提升时会失败。

`NEVER` 不等于自动获得完整权限。

### 2.3 Ephemeral Thread

```java
.ephemeral(true)
```

临时 Thread 只存在于内存中，适合一次性分析或不希望写入会话历史的任务。关闭 app-server 后无法通过 ID 恢复。需要后续 `resumeThread(...)` 时不要启用。

## 3. TurnOptions

```java
TurnOptions turnOptions = TurnOptions.builder()
        .model("gpt-5.5")
        .workingDirectory(Path.of("D:/code/my-project/module-a"))
        .reasoningEffort("high")
        .reasoningSummary(ReasoningSummary.DETAILED)
        .sandboxPolicy(SandboxPolicy.readOnly())
        .approvalPolicy(ThreadOptions.ApprovalPolicy.NEVER)
        .approvalsReviewer(ApprovalsReviewer.USER)
        .clientUserMessageId("message-42")
        .personality(Personality.PRAGMATIC)
        .serviceTier("priority")
        .outputSchema(schema)
        .build();
```

| Builder 方法 | 说明 |
|---|---|
| `model(String)` | 覆盖当前 Turn 使用的模型 |
| `workingDirectory(Path)` | 覆盖当前 Turn 的工作目录 |
| `reasoningEffort(String)` | 推理强度，具体可用值取决于模型 |
| `sandbox(Sandbox)` | 将枚举转换成 app-server `sandboxPolicy` 对象 |
| `sandboxPolicy(SandboxPolicy)` | 完整 read-only/workspace-write/external sandbox 配置 |
| `approvalPolicy(ApprovalPolicy)` | 覆盖审批策略 |
| `granularApprovalPolicy(...)` | 覆盖为字段级审批策略 |
| `approvalsReviewer(ApprovalsReviewer)` | 覆盖审批审查方 |
| `clientUserMessageId(String)` | 关联客户端自己的消息 ID |
| `personality(Personality)` | 覆盖本 Turn 的表达风格 |
| `serviceTier(String)` | 覆盖本 Turn 服务等级 |
| `reasoningSummary(ReasoningSummary)` | 推理摘要模式 |
| `outputSchema(JsonNode)` | 约束最终回答的 JSON Schema |

调用方式：

```java
TurnResult result = thread.run(
        List.of(UserInput.text("只分析 module-a 的公共 API。")),
        turnOptions);
```

## 4. 配置优先级

通常可以按下面的心智模型理解：

```text
app-server 默认配置
        ↓
ThreadOptions 会话设置
        ↓
TurnOptions 本次覆盖
```

`CodexClientConfig.workingDirectory` 只决定子进程启动位置，不参与上述 Codex Thread 设置优先级。

## 5. 推荐配置模板

### 只读审查服务

```java
CodexThread thread = codex.startThread(ThreadOptions.builder()
        .workingDirectory(repository)
        .sandbox(ThreadOptions.Sandbox.READ_ONLY)
        .approvalPolicy(ThreadOptions.ApprovalPolicy.NEVER)
        .developerInstructions("只审查和报告问题，不修改文件。")
        .build());
```

### 交互式开发工具

```java
CodexThread thread = codex.startThread(ThreadOptions.builder()
        .workingDirectory(repository)
        .sandbox(ThreadOptions.Sandbox.WORKSPACE_WRITE)
        .approvalPolicy(ThreadOptions.ApprovalPolicy.ON_REQUEST)
        .build());
```

### 一次性结构化分析

```java
CodexThread thread = codex.startThread(ThreadOptions.builder()
        .workingDirectory(repository)
        .sandbox(ThreadOptions.Sandbox.READ_ONLY)
        .ephemeral(true)
        .build());
```

---

[上一章](02-quickstart.md) · [返回目录](README.md) · [下一章：Thread 生命周期](04-thread-lifecycle.md)
