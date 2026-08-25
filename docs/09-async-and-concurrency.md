# 09. 异步调用、虚拟线程与并发

[上一章](08-approvals-and-security.md) · [返回目录](README.md) · [下一章：原始 JSON-RPC](10-raw-json-rpc.md)

## 1. SDK 的并发模型

每个 `CodexClient` 创建一个 `Executors.newVirtualThreadPerTaskExecutor()`，用于：

- 持续读取 app-server stdout；
- 持续排空 stderr；
- 处理 app-server 发来的审批请求；
- 消费 Turn 事件并构建结果；
- 驱动 Flow 发布器。

虚拟线程让这些阻塞式 I/O 保持简单，同时避免为每个任务占用昂贵的平台线程。

## 2. 异步原始请求

```java
ObjectMapper mapper = new ObjectMapper();

CompletableFuture<JsonNode> future = codex.requestAsync(
        "thread/list",
        mapper.createObjectNode().put("limit", 20));

future.thenAccept(response -> {
    System.out.println(response.toPrettyString());
}).exceptionally(error -> {
    error.printStackTrace();
    return null;
});
```

多个 JSON-RPC 请求可同时在途，SDK 使用 UUID 请求 ID 将响应路由到对应 Future。

## 3. 异步等待 Turn

`startTurn(...)` 本身是同步的：它等待 `turn/start` 返回 Turn ID。实际执行结果可以异步等待：

```java
CodexTurn turn = thread.startTurn("运行测试并总结失败。");

CompletableFuture<TurnResult> future = turn.resultAsync();

future.thenAccept(result -> {
    System.out.println(result.finalResponse());
}).exceptionally(error -> {
    System.err.println("Turn failed: " + error.getMessage());
    return null;
});
```

## 4. 同时订阅事件和结果

正确顺序：

```java
CodexTurn turn = thread.startTurn("执行任务");

turn.events().subscribe(new LoggingSubscriber());

CompletableFuture<TurnResult> resultFuture = turn.resultAsync();
```

订阅事件会启动唯一的事件消费流程；随后 `resultAsync()` 返回同一个流程最终完成的 Future。

错误顺序：

```java
CompletableFuture<TurnResult> future = turn.resultAsync();

// 此时事件已经被内部结果收集器消费，不能再可靠订阅
turn.events().subscribe(new LoggingSubscriber());
```

## 5. 添加异步超时

```java
CompletableFuture<TurnResult> future = turn.resultAsync()
        .orTimeout(15, TimeUnit.MINUTES);

future.exceptionallyCompose(error -> {
    if (unwrap(error) instanceof TimeoutException) {
        try {
            turn.interrupt();
        } catch (RuntimeException interruptError) {
            error.addSuppressed(interruptError);
        }
    }
    return CompletableFuture.failedFuture(error);
});
```

辅助方法：

```java
static Throwable unwrap(Throwable error) {
    Throwable current = error;
    while ((current instanceof CompletionException
            || current instanceof ExecutionException)
            && current.getCause() != null) {
        current = current.getCause();
    }
    return current;
}
```

Future 超时不会自动中断 Codex Turn。除了显式调用 `turn.interrupt()`，也可以让请求
超时或 Web 连接关闭时取消绑定的 Token：

```java
CancellationToken cancellation = new CancellationToken();
CodexTurn turn = thread.startTurn("执行任务", cancellation);

scheduledTimeout.whenComplete((ignored, error) -> cancellation.cancel());
```

`CancellationToken` 可以绑定多个 Turn，第一次 `cancel()` 会为所有仍在运行的 Turn
发送 `turn/interrupt`，之后重复取消返回 `false`。

## 6. 多 Thread 并行

同一个 Client 可以承载多个 Thread：

```java
CodexThread first = codex.startThread(readOnlyOptions(projectA));
CodexThread second = codex.startThread(readOnlyOptions(projectB));

CodexTurn firstTurn = first.startTurn("分析项目 A");
CodexTurn secondTurn = second.startTurn("分析项目 B");

CompletableFuture<Void> all = CompletableFuture.allOf(
        firstTurn.resultAsync(),
        secondTurn.resultAsync());

all.join();

System.out.println(firstTurn.resultAsync().join().finalResponse());
System.out.println(secondTurn.resultAsync().join().finalResponse());
```

辅助方法：

```java
static ThreadOptions readOnlyOptions(Path project) {
    return ThreadOptions.builder()
            .workingDirectory(project)
            .sandbox(ThreadOptions.Sandbox.READ_ONLY)
            .approvalPolicy(ThreadOptions.ApprovalPolicy.NEVER)
            .build();
}
```

实际并发上限仍受模型服务、app-server 队列、机器资源和账户配额影响。

## 7. 同一 Thread 不要并发启动多个 Turn

Thread 通常只有一个活动 Turn。需要并行探索时先 fork：

```java
CodexThread base = codex.resumeThread(threadId);
CodexThread alternative = codex.forkThread(threadId);

CompletableFuture<TurnResult> a = base.startTurn("方案 A：最小修改").resultAsync();
CompletableFuture<TurnResult> b = alternative.startTurn("方案 B：重新设计").resultAsync();

CompletableFuture.allOf(a, b).join();
```

## 8. 一个 Client 还是多个 Client

推荐一个应用实例复用少量长生命周期 Client，而不是每次请求都创建 app-server：

```text
HTTP 请求 1 ─┐
HTTP 请求 2 ─┼── CodexClient ── app-server
HTTP 请求 3 ─┘
```

多个 Client 意味着多个子进程、独立连接和更多内存。只有在需要隔离环境变量、Codex Home、身份或故障域时才创建多个 Client。

## 9. Web 框架集成

不要在请求线程中无限期调用 `thread.run(...)`。更合适的做法是：

1. 创建后台任务记录；
2. 启动 Turn；
3. 通过 `resultAsync()` 更新任务状态；
4. 通过 Flow 事件转发到 SSE/WebSocket；
5. 提供取消接口调用 `CancellationToken.cancel()` 或 `turn.interrupt()`。

## 10. 关闭时的并发行为

关闭 Client 会使未完成 JSON-RPC Future 和活动 Turn 失败，并停止执行器。应用关闭流程应：

1. 停止接收新任务；
2. 等待或中断活动 Turn；
3. 持久化任务状态；
4. 调用 `CodexClient.close()`。

不要在调用 `close()` 后继续使用已有的 `CodexThread` 或 `CodexTurn`，它们都依赖原 Client。

---

[上一章](08-approvals-and-security.md) · [返回目录](README.md) · [下一章：原始 JSON-RPC](10-raw-json-rpc.md)
