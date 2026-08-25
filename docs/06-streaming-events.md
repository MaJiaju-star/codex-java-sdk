# 06. 流式事件与 Flow API

[上一章](05-turns-and-results.md) · [返回目录](README.md) · [下一章：输入与结构化输出](07-inputs-and-structured-output.md)

流式事件可以让 UI 实时展示回答增量、命令执行、文件修改和 Turn 状态，而不必等待整个任务结束。

## 1. 最小 Flow 订阅者

```java
CodexTurn turn = thread.startTurn("运行相关测试并解释失败原因。");

turn.events().subscribe(new Flow.Subscriber<>() {
    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(CodexEvent event) {
        System.out.println(event.method() + ": " + event.params());
    }

    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }

    @Override
    public void onComplete() {
        System.out.println("事件流完成");
    }
});

TurnResult result = turn.await();
```

顺序很重要：先调用 `events().subscribe(...)`，再调用 `await()` 或 `resultAsync()`。

## 2. 为什么不会丢失早到事件

app-server 可能在 `turn/start` 响应返回之前就发送 `turn/started` 或 Item 事件。SDK 会按 Turn ID 暂存这些通知，创建 `CodexTurn` 后放入该 Turn 的事件队列。

因此，即使 Java 在 `startTurn(...)` 返回后才订阅，之前到达的 Turn 事件仍会被消费。这个保证只适用于 Turn 专属事件流，不适用于客户端全局事件流。

## 3. 可复用的订阅者

```java
public final class LoggingSubscriber implements Flow.Subscriber<CodexEvent> {
    private Flow.Subscription subscription;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(CodexEvent event) {
        try {
            log(event);
        } finally {
            subscription.request(1);
        }
    }

    @Override
    public void onError(Throwable error) {
        System.err.println("Codex event stream failed: " + error.getMessage());
    }

    @Override
    public void onComplete() {
        System.out.println("Codex event stream completed");
    }

    private void log(CodexEvent event) {
        System.out.printf("[%s] %s%n", event.method(), event.params());
    }
}
```

每处理一个事件请求下一个，体现了 Flow 背压。简单日志场景也可一次请求 `Long.MAX_VALUE`。

## 4. 常见事件

app-server 事件种类会随版本增加。`event.type()` 把已知方法映射为
`CodexEventType`，未知方法返回 `UNKNOWN`，原始方法名和参数仍分别保留在
`event.method()`、`event.params()` 中。

| 方法 | 用途 |
|---|---|
| `turn/started` | Turn 开始运行 |
| `item/started` | Item 生命周期开始 |
| `item/completed` | Item 完成，包含完整 Item |
| `item/agentMessage/delta` | 代理回答文本增量 |
| `item/reasoning/summaryTextDelta` | 推理摘要增量 |
| `item/commandExecution/outputDelta` | 命令输出增量 |
| `item/fileChange/outputDelta` | 文件修改相关增量 |
| `thread/tokenUsage/updated` | Token 用量更新 |
| `turn/completed` | Turn 结束 |

枚举 switch 应处理 `UNKNOWN`，从而兼容比 SDK 更新的 CLI。

## 5. 提取回答增量

```java
@Override
public void onNext(CodexEvent event) {
    if (event.type() == CodexEventType.AGENT_MESSAGE_DELTA) {
        String delta = event.params().path("delta").asText("");
        System.out.print(delta);
    }
}
```

增量适合实时 UI，但最终持久化应使用 `TurnResult.finalResponse()` 或完成的 `agentMessage` Item。仅拼接 delta 可能受重试、协议调整或多条代理消息影响。

## 6. 观察工具执行

### 6.1 直接处理原始事件

如果业务需要完整协议字段，可以在 Turn 订阅者中解析原始事件：

```java
@Override
public void onNext(CodexEvent event) {
    switch (event.type()) {
        case ITEM_STARTED -> {
            JsonNode item = event.params().path("item");
            if (item.path("type").asText().equals("commandExecution")) {
                System.out.println("开始命令: " + item.path("command").asText());
            }
        }
        case COMMAND_OUTPUT_DELTA ->
                System.out.print(event.params().path("delta").asText(""));
        case ITEM_COMPLETED -> {
            JsonNode item = event.params().path("item");
            if (item.path("type").asText().equals("commandExecution")) {
                System.out.println("命令状态: " + item.path("status").asText());
            }
        }
        case UNKNOWN -> System.out.println("未知事件: " + event.method());
        default -> { }
    }
}
```

这种写法适合协议调试，但应用必须自行用 `threadId/turnId/itemId` 关联开始、输出和
完成事件。PowerShell、CMD、Bash 等 Shell 在协议中都属于 `commandExecution`，不能
根据这个 Item 类型假设实际使用的是 Bash。

### 6.2 推荐：使用 ToolObserver

日志、指标和前端工具卡片优先使用强类型 `ToolObserver`。SDK 已经完成生命周期关联，
`onOutput` 收到的 `context.itemId()` 与对应的开始、完成事件一致：

```java
ToolObserver observer = new ToolObserver() {
    @Override
    public void onStarted(ToolCallContext context) {
        ObjectNode event = mapper.createObjectNode()
                .put("itemId", context.itemId())
                .put("kind", context.kind().name())
                .put("command", context.command());
        ui.publish(context.threadId(), "tool.started", event);
    }

    @Override
    public void onOutput(ToolCallContext context, String delta) {
        ObjectNode event = mapper.createObjectNode()
                .put("itemId", context.itemId())
                .put("delta", limitAndRedact(delta));
        ui.publish(context.threadId(), "tool.output", event);
    }

    @Override
    public void onCompleted(ToolCallResult result) {
        ObjectNode event = mapper.createObjectNode()
                .put("itemId", result.context().itemId())
                .put("successful", result.successful())
                .put("status", result.item().path("status").asText("unknown"));
        ui.publish(result.context().threadId(), "tool.completed", event);
    }
};

CodexClientConfig config = CodexClientConfig.builder()
        .toolObserver(observer)
        .build();
```

当前 `onOutput` 对应命令标准输出/错误输出增量；文件补丁和 MCP 进度仍可通过
`FILE_CHANGE_PATCH_UPDATED`、`MCP_TOOL_CALL_PROGRESS` 原始事件补充展示。
`ToolObserver` 回调在保序的专用虚拟线程执行器中运行，观察器异常会被隔离，但回调
仍应尽快返回。发送到浏览器前至少应：

- 限制单个增量和每个 `itemId` 的累计输出大小；
- 删除 ANSI 控制序列或在安全的终端组件中解析；
- 对 Token、口令、认证头和敏感路径做脱敏；
- 不直接把 `context.raw()` 或完整原始 Item 当成稳定的前端契约。

## 7. 将事件发送到 WebSocket/SSE

在服务端应用中，可以把 Codex 事件转换成自有 DTO，再发送给前端。不要直接承诺 app-server 原始 JSON 是稳定的前端公共协议。

```java
record UiEvent(String type, String text, JsonNode raw) {}

UiEvent toUiEvent(CodexEvent event) {
    return switch (event.type()) {
        case AGENT_MESSAGE_DELTA ->
                new UiEvent("assistant_delta", event.params().path("delta").asText(""), event.params());
        case TURN_COMPLETED ->
                new UiEvent("turn_completed", "", event.params());
        default ->
                new UiEvent("codex_event", "", event.params());
    };
}
```

工具事件建议使用 `itemId` 作为前端卡片键，并使用与 Shell 无关的名字。例如完整案例
采用：

```text
tool.command.started
tool.command.output
tool.command.completed
approval.requested
approval.resolved
```

这样 PowerShell、CMD 和 Bash 可以共用同一套 UI；Shell 类型只作为展示标签，而不是
事件协议的一部分。浏览器异步审批和有界 SSE 回放见第 14 章。

## 8. Turn 事件流是单订阅的

当前 `CodexTurn.events()` 设计为单订阅消费：

```java
Flow.Publisher<CodexEvent> publisher = turn.events();
publisher.subscribe(firstSubscriber);

// 不要再次订阅同一个 Turn
// publisher.subscribe(secondSubscriber);
```

需要多个消费者时，在应用中建立一个订阅者，然后自行广播到日志、UI 和指标系统。

## 9. 全局事件流

`CodexClient.events()` 提供连接级事件：

```java
codex.events().subscribe(new LoggingSubscriber());
```

它与 Turn 事件流不同：

- 不回放订阅前的事件；
- 为避免慢订阅者阻塞 JSON-RPC 读取，全局缓冲区压力过大时允许丢弃事件副本；
- 适合诊断和一般观测；
- 需要可靠构建 Turn 结果时必须使用 `CodexTurn` 的专属流。

## 10. 错误与完成

事件消费线程、客户端关闭或 app-server 断开时会触发 `onError`。正常收到 `turn/completed` 后触发 `onComplete`。

建议订阅者同时处理事件流错误和结果 Future 错误：

```java
CompletableFuture<TurnResult> resultFuture = turn.resultAsync();
resultFuture.whenComplete((result, error) -> {
    if (error != null) {
        System.err.println("Turn failed: " + error.getMessage());
    } else {
        System.out.println("Final: " + result.finalResponse());
    }
});
```

---

[上一章](05-turns-and-results.md) · [返回目录](README.md) · [下一章：输入与结构化输出](07-inputs-and-structured-output.md)
