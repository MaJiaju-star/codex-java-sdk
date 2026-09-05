# Solon Web SSE 多会话完整案例

本章把前面的 SDK 能力组合成一个可运行的 Web 应用。源码位于：

```text
sdk/java/examples/solon-sse-chat
```

它不是只有一个流式接口的片段，而是包含了会话状态管理、强类型 Thread 分页与归档、
完整 Thread/Turn 配置、过载重试、结构化异常、SSE 回放、浏览器切换、Turn 中断、
工具事件转换、浏览器异步审批、Shell 路径观察和递归目录监听的完整最小系统。

## 1. 最终能力

启动后访问 `http://localhost:8080/`，可以完成以下操作：

1. 指定工作目录并创建新的 Codex Thread；
2. 输入已有 Thread ID，恢复历史对话；
3. 在同一会话中连续发送多轮消息；
4. 实时显示 Codex 回答增量；
5. 同时创建多个会话并随时切换；
6. 切换页面会话时，让原会话的 Turn 在服务器后台继续执行；
7. 重新连接后回放最近 500 条事件；
8. 从 Codex Thread 读取持久 Turn 历史，并与实时 SSE 去重合并；
9. 打断当前会话正在执行的 Turn；
10. 在对话流中查看 PowerShell、CMD、Bash 等命令的实时终端输出；
11. 在浏览器批准或拒绝命令、文件修改请求；
12. 查看 MCP 调用、文件修改、Web Search 和 Token 用量；
13. 查看工作目录中的新增、修改与删除事件；
14. 搜索并分页浏览 Codex 持久 Thread；
15. 归档、查看已归档 Thread、取消归档并恢复；
16. 在页面选择 Personality、Service Tier、Reasoning Effort 和 Reasoning Summary；
17. 查看细分异常类型、JSON-RPC code 和是否可重试。

## 2. 运行前准备

需要：

- JDK 25；
- Maven 3.9 或更高版本；
- 已安装且完成登录的 `codex` CLI；
- 命令行能够运行 `codex app-server`。

验证环境：

```powershell
java -version
mvn -version
codex --version
```

本案例依赖当前仓库内尚未发布到 Maven Central 的 SDK。因此第一步必须先安装 SDK 到本地仓库：

```powershell
cd D:\code\claude-code-project\codex-main\sdk\java
mvn clean install
```

构建案例：

```powershell
cd examples\solon-sse-chat
mvn clean package
```

限制网页可访问的工作目录根路径，再启动服务：

```powershell
$env:CODEX_EXAMPLE_WORKSPACE_ROOT = "D:\code"
$env:CODEX_EXAMPLE_MODEL = "gpt-5.5"
java -jar target\codex-solon-sse-chat.jar
```

也可以在开发阶段直接运行：

```powershell
mvn solon:run
```

浏览器打开：

```text
http://localhost:8080/
```

若没有设置 `CODEX_EXAMPLE_WORKSPACE_ROOT`，服务启动目录就是允许的根目录。生产服务不应把磁盘根目录配置成工作区根路径。

案例默认显式使用 `gpt-5.5`，避免继承本机旧配置中的不兼容模型。可以通过 `CODEX_EXAMPLE_MODEL` 选择账户实际支持的其他模型。

## 3. 工程结构

```text
solon-sse-chat/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/openai/codex/example/sse/
    │   ├── App.java
    │   ├── controller/
    │   │   ├── ChatController.java
    │   │   └── HomeController.java
    │   ├── model/
    │   │   └── ApiModels.java
    │   └── service/
    │       ├── BashCommandMonitor.java
    │       ├── ChatSession.java
    │       ├── CodexSessionService.java
    │       ├── SseChannel.java
    │       └── WorkspaceWatcher.java
    └── resources/
        ├── app.yml
        └── static/index.html
```

各组件职责如下：

| 组件 | 职责 |
|---|---|
| `App` | 启动 Solon |
| `ChatController` | 暴露 HTTP 与 SSE API |
| `HomeController` | 将根路径重定向到静态首页 |
| `CodexSessionService` | 维护所有网页会话，共享一个 `CodexClient` |
| `ChatSession` | 持有一个 Codex Thread、活动 Turn、SSE 通道和目录监听器 |
| `SseChannel` | 事件序列化、广播、心跳及最近 500 条回放 |
| `ThreadHistoryMapper` | 将 `thread/read` 原始结果转换成有界、稳定的浏览器历史 DTO |
| `BashCommandMonitor` | 对 PowerShell、CMD、Bash 等命令做尽力分类并提取疑似路径；名称为历史遗留 |
| `WorkspaceWatcher` | 使用 `WatchService` 递归监听真实文件系统变化 |
| `index.html` | 会话列表、聊天区、工具/目录/原始事件面板 |

## 4. Maven 与 Solon 配置

案例使用 Solon 4.0.3 父 POM，并显式依赖 Web、SSE 和 Reactor 适配模块：

```xml
<parent>
  <groupId>org.noear</groupId>
  <artifactId>solon-parent</artifactId>
  <version>4.0.3</version>
  <relativePath />
</parent>

<properties>
  <java.version>25</java.version>
  <maven.compiler.release>25</maven.compiler.release>
</properties>

<dependencies>
  <dependency>
    <groupId>org.noear</groupId>
    <artifactId>solon-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.noear</groupId>
    <artifactId>solon-web-sse</artifactId>
  </dependency>
  <dependency>
    <groupId>org.noear</groupId>
    <artifactId>solon-web-rx</artifactId>
  </dependency>
  <dependency>
    <groupId>org.noear</groupId>
    <artifactId>solon-web-staticfiles</artifactId>
  </dependency>
  <dependency>
    <groupId>org.noear</groupId>
    <artifactId>solon-logging-logback-jakarta</artifactId>
  </dependency>
  <dependency>
    <groupId>io.github.majiajustar</groupId>
    <artifactId>codex-java-sdk</artifactId>
    <version>0.0.4-SNAPSHOT</version>
  </dependency>
</dependencies>
```

`solon-web-rx` 很重要。`ChatController` 返回 Reactor `Flux<String>`，所以不要依赖其他组件偶然传递 `reactor-core`。`solon-web-staticfiles` 用于提供 `src/main/resources/static/index.html`，`HomeController` 再把根路径重定向到该页面。`solon-logging-logback-jakarta` 让启动和请求异常进入日志，避免服务端只返回空的 500 而没有诊断信息。

`app.yml` 启用 Solon 的虚拟线程：

```yaml
server.port: 8080
server.request.encoding: utf-8
server.response.encoding: utf-8

solon.app:
  name: codex-solon-sse-chat

solon.threads.virtual.enabled: true
```

SDK 内部也使用 JDK 虚拟线程处理 app-server 标准输入输出和异步任务。Solon 虚拟线程负责 HTTP 请求，两者职责不同。

## 5. 为什么使用“持久 SSE + 独立 POST”

常见的简单实现会让“发送消息”接口本身返回 SSE。它适合单轮演示，但不适合多会话页面：

- 切换页面时很难继续观察原 Turn；
- 中断、工具事件和目录事件缺少统一通道；
- 没有新消息时无法持续推送工作区变化；
- 浏览器重连后不容易回放状态。

本案例把命令和事件分开：

```text
浏览器 --POST /messages--> ChatSession --startTurn--> Codex app-server
浏览器 <--GET /events SSE-- SseChannel <--Codex/WatchService-- ChatSession
```

发送消息的 POST 只返回已接受的 Turn ID：

```json
{
  "sessionId": "8f9...",
  "turnId": "turn_..."
}
```

回答增量、工具信息、完成状态和目录变化都从长期存在的 `/events` 连接返回。

## 6. HTTP API

### 6.1 服务信息

```http
GET /api/codex/info
```

响应示例：

```json
{
  "workspaceRoot": "D:\\code",
  "model": "gpt-5.5",
  "javaVersion": "25.0.1+8"
}
```

### 6.2 创建或恢复会话

```http
POST /api/codex/sessions
Content-Type: application/json
```

创建新 Thread：

```json
{
  "name": "修复后端测试",
  "workspace": "codex-main",
  "threadId": "",
  "personality": "pragmatic",
  "serviceTier": "priority"
}
```

恢复已有 Thread：

```json
{
  "name": "继续昨天的任务",
  "workspace": "codex-main",
  "threadId": "019...",
  "personality": "friendly",
  "serviceTier": ""
}
```

`workspace` 可以是根目录内的相对路径，也可以是根目录内的绝对路径。规范化之后超出 `CODEX_EXAMPLE_WORKSPACE_ROOT` 会被拒绝。

### 6.3 列出网页会话

```http
GET /api/codex/sessions
```

“网页会话 ID”和“Codex Thread ID”不是同一个概念：

- `id` 是本次服务进程内生成的 UUID，用于选中 `ChatSession`；
- `threadId` 是 app-server 管理的持久对话标识，可用于以后恢复。

### 6.4 强类型 Thread 分页与归档

查询未归档 Thread：

```http
GET /api/codex/threads?archived=false&limit=20&searchTerm=payment
```

后端用 `ThreadListOptions` 构造过滤条件，固定按
`ThreadSortKey.RECENCY_AT + SortDirection.DESC` 排序，并直接返回生成的
`ThreadListResponse`。下一页使用响应中的 `nextCursor`：

```http
GET /api/codex/threads?archived=false&limit=20&cursor=<nextCursor>
```

归档：

```http
POST /api/codex/threads/archive
Content-Type: application/json

{"threadId":"019..."}
```

如果该 Thread 在当前 Web 服务中仍有活动 Turn，案例返回 HTTP 409，要求先等待完成
或打断。归档成功后，关联的内存 `ChatSession`、目录监听和 SSE 通道会被关闭。

取消归档：

```http
POST /api/codex/threads/unarchive
Content-Type: application/json

{"threadId":"019..."}
```

取消归档返回强类型 `ThreadUnarchiveResponse`。它只恢复持久 Thread，不会自动创建
网页会话；前端随后使用普通 `POST /sessions` 和该 `threadId` 恢复。

### 6.5 读取持久历史

```http
GET /api/codex/history?sessionId=<网页会话ID>
```

响应使用案例自己的 DTO，而不是直接暴露 app-server 原始 JSON：

```json
{
  "sessionId": "e714...",
  "threadId": "019...",
  "turns": [
    {
      "id": "019...",
      "status": "completed",
      "startedAt": 1784768962,
      "completedAt": 1784769020,
      "durationMs": 57413,
      "items": [
        {
          "id": "item-1",
          "type": "userMessage",
          "text": "检查当前目录"
        },
        {
          "id": "item-2",
          "type": "agentMessage",
          "phase": "final_answer",
          "text": "检查完成。"
        }
      ]
    }
  ]
}
```

历史 DTO 还支持命令、文件修改、MCP 和 Web Search 摘要。历史命令输出最多返回约
100 KB，并经过与实时输出相同的常见密钥脱敏。工具 Item 是否存在取决于 Thread
历史模式和 CLI 可恢复的数据；不能假设每个执行过的命令都会出现在旧历史中。

### 6.6 订阅 SSE

```http
GET /api/codex/events?sessionId=<网页会话ID>
Accept: text/event-stream
```

浏览器使用：

```javascript
const source = new EventSource(
  '/api/codex/events?sessionId=' + encodeURIComponent(sessionId)
);

source.onmessage = event => {
  const envelope = JSON.parse(event.data);
  console.log(envelope.type, envelope.data);
};
```

### 6.7 发送消息

```http
POST /api/codex/messages
Content-Type: application/json

{
  "sessionId": "网页会话ID",
  "message": "检查项目并运行相关测试",
  "reasoningEffort": "high",
  "reasoningSummary": "detailed"
}
```

服务使用完整 `TurnOptions`：为浏览器消息生成 `clientUserMessageId`，按请求设置推理
强度和摘要，并用 `SandboxPolicy.workspaceWrite().writableRoot(workspace)` 把本 Turn
的写入范围限定在会话工作目录。同一 ChatSession 同一时间只允许一个活动 Turn。

### 6.8 打断 Turn

```http
POST /api/codex/interrupt
Content-Type: application/json

{
  "sessionId": "网页会话ID"
}
```

服务调用当前 `CodexTurn.interrupt()`。打断是向 app-server 发出的协议请求，不是粗暴终止 JVM 线程或整个 Codex 子进程，所以其他会话不会受影响。

### 6.9 处理工具审批

```http
POST /api/codex/approvals
Content-Type: application/json

{
  "sessionId": "网页会话ID",
  "approvalId": "approval.requested 事件中的 ID",
  "decision": "accept"
}
```

`decision` 只接受 `accept`、`acceptForSession`、`decline`、`cancel`。服务端同时校验
`approvalId` 属于指定网页会话；未知、重复或超时的审批返回 HTTP 400。

### 6.10 关闭网页会话

```http
DELETE /api/codex/sessions?sessionId=<网页会话ID>
```

关闭会：

1. 尝试打断活动 Turn；
2. 用 `CANCEL` 完成所有待处理审批；
3. 关闭递归目录监听；
4. 完成 SSE 与心跳流；
5. 从内存会话表和 Thread 索引移除该会话。

它不会删除持久 Codex Thread。只要保存了 `threadId`，以后仍可恢复。

### 6.11 结构化异常

案例把 SDK 细分异常映射为稳定 JSON：

```json
{
  "error": "Server overloaded; retry later.",
  "type": "ServerBusyException",
  "code": -32001,
  "retryable": true
}
```

- 参数错误返回 400；
- 当前状态冲突返回 409；
- 过载、传输关闭和请求超时返回 503，但只有明确的过载标为 `retryable=true`；
- 其他 JSON-RPC 错误返回 502。

SDK 已经按照 `RetryPolicy.overloadDefaults()` 对明确过载做指数退避；HTTP 层的
`retryable` 是给浏览器或网关决定是否再次提交整个业务操作的提示，不代表所有 503
都可以无条件重复写操作。

## 7. SSE 信封与回放

所有事件统一编码为：

```json
{
  "sequence": 42,
  "type": "assistant.delta",
  "sessionId": "8f9...",
  "time": "2026-07-23T03:12:00Z",
  "data": {
    "delta": "正在检查测试……"
  }
}
```

关键字段：

- `sequence`：该网页会话内递增的事件序号；
- `type`：页面可以稳定分派的案例事件类型；
- `sessionId`：防止切换时把事件画到错误会话；
- `time`：UTC ISO-8601 时间；
- `data`：对应事件载荷。

`SseChannel` 保存最近 500 条非心跳事件。新订阅者连接时先收到这些历史，再接收实时事件。前端按 `sequence` 去重，因此来回切换会话不会重复绘制相同内容。

这是进程内、固定容量回放，不是持久事件存储。服务重启后回放消失。生产系统可以把信封写入 Redis Streams、Kafka 或数据库，并结合浏览器的 `Last-Event-ID` 做精确续传。

每 15 秒发送一次 `heartbeat`，用于让代理服务器和浏览器保持连接。会话关闭时，心跳与事件 Flux 会一起完成。

## 8. 从 Codex Flow 事件桥接到 SSE

`CodexTurn.events()` 是单订阅流。案例严格遵守“先订阅、再获取结果 Future”的顺序：

```java
CodexTurn turn = thread.startTurn(message);
activeTurn.set(turn);

turn.events().subscribe(new TurnEventSubscriber(turn));
turn.resultAsync().whenComplete((result, error) -> complete(turn, result, error));
```

订阅器采用逐条背压：

```java
public void onSubscribe(Flow.Subscription subscription) {
    this.subscription = subscription;
    subscription.request(1);
}

public void onNext(CodexEvent event) {
    try {
        route(event);
    } finally {
        subscription.request(1);
    }
}
```

不要为日志、WebSocket 和 SSE 分别订阅三次 `CodexTurn.events()`。正确方式是只订阅一次，再由应用自己的事件总线扇出。

## 9. 案例事件类型

| 案例事件 | 来源或含义 |
|---|---|
| `session.ready` | ChatSession 初始化完成 |
| `user.message` | 服务器接受用户消息 |
| `turn.accepted` | `turn/start` 已返回 Turn ID |
| `turn.started` | app-server 的 Turn 开始通知 |
| `assistant.delta` | 回答文本增量 |
| `assistant.reasoning` | 推理或摘要文本增量 |
| `assistant.final` | SDK 汇总后的最终回答 |
| `turn.completed` | SDK 最终结果，包含状态和用量 |
| `turn.error` | 启动或执行失败 |
| `turn.interrupting` | 已发起打断请求 |
| `tool.command.started/completed` | 跨平台命令执行生命周期，按 `itemId` 关联 |
| `tool.command.output` | PowerShell、CMD、Bash 等命令的标准输出/错误输出增量 |
| `tool.mcp.started/completed` | MCP 工具调用 |
| `tool.fileChange.started/completed` | 文件变更工具 |
| `tool.webSearch.started/completed` | Web Search Item |
| `approval.requested/resolved` | 浏览器审批请求及最终决定 |
| `usage.updated` | Token 用量更新 |
| `workspace.changed` | `WatchService` 观察到文件系统变化 |

页面右侧“工具”页签显示工具和用量，“目录”页签显示文件系统变化，“事件”页签显示完整案例事件。

## 10. 多会话切换为什么不会中断 Turn

前端切换时只做两件事：

```javascript
state.source?.close();
state.source = new EventSource(
  '/api/codex/events?sessionId=' + encodeURIComponent(nextSessionId)
);
```

它没有调用 `/interrupt`，也没有关闭服务端 `ChatSession`。因此：

- 原 CodexTurn 继续消费 app-server 事件；
- 事件继续进入原会话的有界历史；
- 用户切回原会话时，SSE 重连并回放遗漏事件；
- 另一会话可以并行运行自己的 Turn。

当前页面状态保存在浏览器内存中，刷新页面后会重新拉取会话列表并从服务器回放最近事件。

### 持久历史与实时事件如何合并

切换会话时前端按以下顺序工作：

1. 调用 `/history` 读取已完成 Turn；
2. 把 `userMessage`、`agentMessage` 和工具摘要转换成聊天条目；
3. 再建立 `/events` SSE 连接；
4. SSE 回放中若事件的 `turnId` 已存在于完成历史，则跳过聊天区重复渲染；
5. `inProgress` Turn 不从历史快照渲染，继续由 SSE 增量驱动。

因此历史负责“已经完成的过去”，SSE 负责“正在发生的现在”。服务端为
`user.message`、`assistant.delta`、最终消息和工具事件携带 `turnId`，前端才能可靠
去重。原始事件仍会保留在右侧事件检查器中，聊天区去重不会删除诊断信息。

这里的历史是 Codex 持久 Thread 能恢复的 Turn/Item，不是模型内部上下文窗口的逐字
镜像。压缩后的上下文、开发者指令和某些工具细节不一定表现为普通历史消息。

## 11. 跨平台命令监听与浏览器审批

案例把 SDK 的 `ToolObserver` 注册为全局观察器，再用 `threadId` 找到网页会话。这样
PowerShell、CMD、Bash 或其他命令都使用相同的 `tool.command.*` 事件，而不是把协议中的
`commandExecution` 错误等同于 Bash。每个事件携带 `itemId`，前端据此把输出增量追加到
正确的工具卡片。

当命令工具开始时，案例会：

1. 只向浏览器发送所需字段，不转发完整原始 Item；
2. 提取并展示命令文本；
3. 根据首个命令词把操作粗略分为 `read/create/delete/edit/copy/move/execute`；
4. 用轻量引号感知分词提取疑似路径；
5. 相对路径以会话工作目录为基准解析；
6. 标注规范化路径是否位于工作目录内。

命令输出通过 `ToolObserver.onOutput(context, delta)` 到达，服务端把单个增量限制为
16 KiB，浏览器把每个工具卡片的累计终端文本限制为约 100 KB。事件历史仍受 500 条
上限约束。服务端会尽力遮蔽常见的 `apiKey/token/password/secret=...`，但流式分片可能
从敏感值中间切开，因此生产环境仍应在命令执行侧和日志系统中实施正式的脱敏策略。

审批处理器不会自动接受，而是返回一个尚未完成的 `CompletableFuture`：

```java
.approvalHandler(this::requestApproval)
.toolObserver(new BrowserToolObserver())
```

服务端立即发布 `approval.requested`，浏览器可提交以下决定到
`POST /api/codex/approvals`：

- `accept`：仅允许这一次；
- `acceptForSession`：在当前 Codex 会话内允许同类请求；
- `decline`：拒绝本次操作，让 Turn 继续处理拒绝结果；
- `cancel`：取消此次审批请求。

请求体示例：

```json
{
  "sessionId": "网页会话 ID",
  "approvalId": "approval.requested 中的 ID",
  "decision": "accept"
}
```

每个 `approvalId` 只能成功处理一次，未知值会返回 400；9 分钟未处理会自动得到
`cancel`。关闭网页会话时，所有待处理审批也会被取消，避免 app-server 永久等待。

输出结构示例：

```json
{
  "operation": "delete",
  "command": "rm ./tmp/output.txt",
  "paths": [
    {
      "token": "./tmp/output.txt",
      "resolvedPath": "D:\\code\\project\\tmp\\output.txt",
      "insideWorkspace": true
    }
  ]
}
```

这里刻意称为 Monitor，而不是 Validator。Shell 语法非常复杂，包括管道、变量、命令替换、重定向、符号链接、脚本二次执行和不同平台语法。轻量解析器不可能成为可靠安全边界。

如果要实施安全控制，应结合：

- `ThreadOptions.Sandbox`；
- 强类型 `ApprovalHandler` 与 `ToolInterceptor` 审批；
- 独立低权限系统账号；
- 容器或虚拟机隔离；
- 允许命令/路径策略；
- 审计日志和人工批准。

## 12. 递归工作目录监听

`WorkspaceWatcher` 使用标准 JDK `WatchService`，启动一个虚拟线程持续读取变化：

```java
watchService = FileSystems.getDefault().newWatchService();
registerTree(workspace);
worker = Thread.ofVirtual().start(this::watch);
```

初始化时递归注册已有目录；发现新目录后继续递归注册。事件被转成：

```json
{
  "type": "workspace.changed",
  "data": {
    "kind": "modified",
    "path": "src/main/java/App.java"
  }
}
```

为了减少噪声，案例忽略：

```text
.git, .idea, .vscode, node_modules, target, build, dist
```

需要理解几个限制：

- 操作系统可能合并连续修改，事件不是事务日志；
- 大量变化可能产生 `overflow`；
- 重命名一般表现为删除加创建；
- 监听到变化不代表一定由当前 Shell 命令造成，也可能由 IDE、构建工具或其他进程造成；
- 事件用于刷新 UI 和审计提示，不应直接作为权限判定依据。

## 13. 工作目录边界

`CodexSessionService` 会规范化用户提交的目录并检查：

```java
Path resolved = (path.isAbsolute() ? path : workspaceRoot.resolve(path))
        .normalize()
        .toAbsolutePath();

if (!resolved.startsWith(workspaceRoot)) {
    throw new IllegalArgumentException("工作目录必须位于允许的根目录内");
}
```

之后还会要求目录真实存在并调用 `toRealPath()`。这能阻止普通的 `../` 逃逸。生产场景还应在部署层限制服务进程能访问的磁盘范围，因为应用层路径检查不能替代操作系统隔离。

## 14. Turn 中断与竞态处理

活动 Turn 存在 `AtomicReference<CodexTurn>` 中：

```java
private final AtomicReference<CodexTurn> activeTurn = new AtomicReference<>();
```

完成回调使用：

```java
activeTurn.compareAndSet(turn, null);
```

这样旧 Turn 的迟到回调不会意外清除未来的新 Turn。`send` 方法还使用同步区段，保证检查“没有活动 Turn”和设置新 Turn 是一个逻辑整体。

浏览器点击打断后先收到 `turn.interrupting`。最终仍应等待 `turn.completed` 或 `turn.error`，不要看到“已请求打断”就假设远端工作已经结束。

## 15. 前端如何组合增量文本

前端不依赖 React 或 Vue。它按事件顺序重建消息：

```javascript
if (event.type === 'assistant.delta') {
  let last = messages.at(-1);
  if (!last || last.role !== 'assistant') {
    last = {role:'assistant', text:''};
    messages.push(last);
  }
  last.text += event.data.delta || '';
}
```

收到 `assistant.final` 后用 SDK 聚合出的最终文本校正结果。这可以处理增量漏包、重连和最终内容修订。

演示页面把收到的事件保存在浏览器内存，每个会话最多接受服务器回放的 500 条。大型应用应对聊天消息、终端输出和审计事件分别分页，不要无限保存到 DOM。

## 16. 用 curl 验证接口

创建会话：

```powershell
$session = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/codex/sessions `
  -ContentType application/json `
  -Body '{"name":"CLI 测试","workspace":"codex-main","threadId":""}'

$session
```

另开终端监听 SSE：

```powershell
curl.exe -N "http://localhost:8080/api/codex/events?sessionId=$($session.id)"
```

读取持久历史：

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/codex/history?sessionId=$($session.id)" |
  ConvertTo-Json -Depth 8
```

发送消息：

```powershell
$body = @{
  sessionId = $session.id
  message = "列出当前目录，并解释主要模块"
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/codex/messages `
  -ContentType application/json `
  -Body $body
```

如果 SSE 终端出现 `approval.requested`，复制其中的 `approvalId` 并允许一次：

```powershell
$approval = @{
  sessionId = $session.id
  approvalId = "从 SSE 复制的 approvalId"
  decision = "accept"
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/codex/approvals `
  -ContentType application/json `
  -Body $approval
```

打断：

```powershell
$body = @{ sessionId = $session.id } | ConvertTo-Json
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/codex/interrupt `
  -ContentType application/json `
  -Body $body
```

## 17. 生产化改造清单

这个项目是完整可运行案例，但不是开箱即用的多租户生产系统。上线前至少处理：

1. **身份认证**：会话必须绑定登录用户，所有接口验证所有权；
2. **审批策略**：保留案例的会话归属校验，并在人工审批前增加服务端命令、路径策略；
3. **资源配额**：限制用户会话数、并发 Turn、执行时长、SSE 连接数和输出大小；
4. **持久状态**：保存网页会话与 Thread ID 的映射；
5. **历史分页**：案例一次读取全部可用 Turn，长会话应提供游标分页和响应大小上限；
6. **分布式事件**：多实例部署时使用外部消息系统，不依赖本机 Map；
7. **可靠续传**：支持 SSE `id` 与 `Last-Event-ID`；
8. **反向代理**：关闭 SSE 响应缓冲，设置合理的空闲超时；
9. **异常治理**：把案例的控制器级映射收敛为统一异常处理器、业务错误码和可观测指标；
10. **日志脱敏**：命令、路径、提示词和工具输出可能包含密钥；
11. **进程治理**：监控 app-server 健康状态，制定异常退出和重启策略；
12. **磁盘隔离**：每个租户使用独立目录、账号或容器；
13. **事件分层**：回答、工具输出、审计记录使用不同保留期限。

## 18. 常见问题

### 创建会话时报找不到 `codex`

保证启动 Solon 的同一环境中能执行 `codex app-server`。Windows SDK 默认通过：

```text
cmd.exe /d /c codex app-server
```

如果服务账号的 `PATH` 不同，应在自己的生产封装中使用 `CodexClientConfig.codexExecutable(...)` 指定路径。

### Maven 找不到 `io.github.majiajustar:codex-java-sdk`

先在 `sdk/java` 执行：

```powershell
mvn clean install
```

### SSE 建立但没有回答

检查：

- 是否订阅了正确的网页 `sessionId`；
- `/messages` 是否返回 Turn ID；
- Codex CLI 是否已登录；
- 反向代理是否缓冲 `text/event-stream`；
- 右侧“事件”页签是否出现 `turn.error`。

### 为什么切回会话后看到事件序号跳跃

心跳也会取得序号，但不会放入历史和事件面板，所以非心跳事件序号允许不连续。序号只用于排序和去重，不表示业务事件总数。

### 为什么目录面板出现不是 Codex 造成的变化

`WatchService` 观察整个工作目录。IDE、Git、编译器和用户自己的命令也会触发事件。这正是目录监听与命令工具监听同时存在的原因：前者回答“磁盘发生了什么”，后者回答“Codex 请求执行了什么”。两者只能关联分析，不能简单等同。

---

上一章：[公开 API 速查](13-api-reference.md)

返回：[教程目录](README.md)

下一章：[从 app-server Schema 生成 Java 类型](15-schema-code-generation.md)
