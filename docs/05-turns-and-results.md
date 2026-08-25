# 05. 执行 Turn 与处理结果

[上一章](04-thread-lifecycle.md) · [返回目录](README.md) · [下一章：流式事件](06-streaming-events.md)

Turn 是一次具体任务的执行边界。用户输入、模型输出、命令、文件修改和工具调用都归属于某个 Turn。

## 1. `run` 与 `startTurn` 的区别

最简单的同步调用：

```java
TurnResult result = thread.run("解释项目的构建流程。");
```

它内部等价于：

```java
CodexTurn turn = thread.startTurn("解释项目的构建流程。");
TurnResult result = turn.await();
```

区别是 `startTurn(...)` 会先返回 `CodexTurn`，允许应用订阅事件、追加输入或中断；`run(...)` 直接等待完成，适合不需要中间状态的后台任务。

## 2. 使用多个输入项

```java
List<UserInput> inputs = List.of(
        UserInput.text("比较这两张界面截图并指出回归。"),
        UserInput.localImage(Path.of("D:/artifacts/before.png")),
        UserInput.localImage(Path.of("D:/artifacts/after.png")));

TurnResult result = thread.run(inputs, TurnOptions.defaults());
```

`run(List<UserInput>, TurnOptions)` 要求列表非空，否则抛出 `IllegalArgumentException`。

## 3. Turn 级配置

```java
TurnOptions options = TurnOptions.builder()
        .model("gpt-5.5")
        .reasoningEffort("high")
        .reasoningSummary(ReasoningSummary.DETAILED)
        .personality(Personality.PRAGMATIC)
        .serviceTier("priority")
        .clientUserMessageId("web-message-42")
        .workingDirectory(Path.of("D:/code/my-project/server"))
        .sandboxPolicy(SandboxPolicy.readOnly())
        .approvalPolicy(ThreadOptions.ApprovalPolicy.NEVER)
        .build();

TurnResult result = thread.run(
        List.of(UserInput.text("分析 server 模块的并发安全问题。")),
        options);
```

这适合在同一个 Thread 中临时切换子目录、模型或权限。

需要完整工作区写策略时：

```java
SandboxPolicy.WorkspaceWrite sandbox = SandboxPolicy.workspaceWrite()
        .writableRoot(Path.of("D:/code/my-project"))
        .networkAccess(false)
        .excludeTmpdirEnvVar(true)
        .excludeSlashTmp(true)
        .build();
```

`SandboxPolicy.externalSandbox(NetworkAccess)`、结构化输出的 `outputSchema(...)`，
以及 `GranularApprovalPolicy` 也都对应当前 `TurnStartParams` Schema。

## 4. TurnResult 字段

`TurnResult` 是 Java record：

```java
public record TurnResult(
        String id,
        String status,
        String finalResponse,
        List<JsonNode> items,
        JsonNode usage,
        JsonNode turn) {}
```

### 4.1 id

Turn 的不透明标识：

```java
String turnId = result.id();
```

它可用于日志关联，也可作为原始 JSON-RPC 调用中的 `turnId`。

### 4.2 status

```java
switch (result.status()) {
    case "completed" -> System.out.println("执行成功");
    case "interrupted" -> System.out.println("执行被中断");
    default -> System.out.println("状态: " + result.status());
}
```

失败状态通常会在结果生成前转成异常，因此不要只依赖 `status` 处理失败。

### 4.3 finalResponse

SDK 从已完成的 `agentMessage` Item 中提取最终回答，优先选择 `phase == "final_answer"`：

```java
String answer = result.finalResponse();
if (answer == null) {
    System.out.println("Turn 没有产生最终代理消息");
} else {
    System.out.println(answer);
}
```

被中断的 Turn、只产生工具事件的 Turn 或协议版本差异都可能导致该字段为空。

### 4.4 items

```java
for (JsonNode item : result.items()) {
    String type = item.path("type").asText("unknown");
    switch (type) {
        case "agentMessage" ->
                System.out.println("Assistant: " + item.path("text").asText());
        case "commandExecution" ->
                System.out.println("Command: " + item.path("command").asText());
        case "fileChange" ->
                System.out.println("File change: " + item.toPrettyString());
        default ->
                System.out.println(type + ": " + item.toPrettyString());
    }
}
```

使用 `path(...)` 比 `get(...)` 更适合兼容可选字段，因为字段不存在时会返回 MissingNode，而不是 `null`。

### 4.5 usage

Token 用量来自 `thread/tokenUsage/updated` 事件：

```java
JsonNode usage = result.usage();
if (usage != null) {
    System.out.println(usage.toPrettyString());
}
```

不同版本的用量结构可能不同，不要在未检查字段时直接调用 `get(...).asLong()`。

### 4.6 turn

`turn()` 保存 `turn/completed` 通知中的原始 Turn 对象：

```java
JsonNode rawTurn = result.turn();
long startedAt = rawTurn.path("startedAt").asLong(0);
long completedAt = rawTurn.path("completedAt").asLong(0);
long durationMs = rawTurn.path("durationMs").asLong(0);
```

## 5. 追加指令 `steer`

活动 Turn 执行期间可以追加文本输入：

```java
CodexTurn turn = thread.startTurn("检查整个项目并找出测试失败原因。");

JsonNode response = turn.steer("优先检查最近修改过的 Java 文件，不要运行全量测试。");
System.out.println("Steer response: " + response);

TurnResult result = turn.await();
```

`steer` 要求该 Turn 仍是 Thread 的活动 Turn。执行已经完成时调用，通常会收到 JSON-RPC 错误。

当前便捷方法只接受字符串。需要图片或 Skill 等追加输入时，可使用原始 `turn/steer` 方法。

## 6. 中断 Turn

```java
CodexTurn turn = thread.startTurn("执行较长的分析任务。请扫描全部模块。");

// 在应用的取消回调或超时处理里调用
turn.interrupt();

TurnResult result = turn.await();
System.out.println(result.status());
```

`interrupt()` 发送中断请求，但不会强制杀死整个 app-server。中断是协作式操作，最终仍应消费到 `turn/completed`。

应用级总超时示例：

```java
CodexTurn turn = thread.startTurn("执行任务");

try {
    TurnResult result = turn.resultAsync().get(10, TimeUnit.MINUTES);
} catch (TimeoutException timeout) {
    turn.interrupt();
    throw timeout;
}
```

## 7. Turn 失败

当 `turn/completed` 中的状态为 `failed` 时，SDK 使用错误消息抛出 `CodexException`。同步 `await()` 基于 `CompletableFuture.join()`，因此调用方有时会看到外层 `CompletionException`：

```java
try {
    TurnResult result = thread.run("执行任务");
} catch (CompletionException error) {
    Throwable cause = error.getCause();
    if (cause instanceof CodexException codexError) {
        System.err.println(codexError.getMessage());
    } else {
        throw error;
    }
}
```

JSON-RPC 请求本身失败则会产生 `JsonRpcException`，它带有错误码和原始 `data`。

## 8. 不要并行消费同一个 Turn

一个 `CodexTurn` 的事件队列只允许启动一次消费流程：

- 调用 `events().subscribe(...)` 会开始消费；
- 调用 `resultAsync()` 或 `await()` 也会在尚未消费时开始消费；
- 如果需要事件和最终结果，应先订阅 `events()`，再调用 `resultAsync()` 或 `await()`；
- 不要对同一个 Turn 创建多个订阅者。

下一章会给出完整的正确订阅方式。

---

[上一章](04-thread-lifecycle.md) · [返回目录](README.md) · [下一章：流式事件](06-streaming-events.md)
