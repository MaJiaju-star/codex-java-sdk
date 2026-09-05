# 04. Thread 生命周期管理

[上一章](03-configuration.md) · [返回目录](README.md) · [下一章：Turn 与结果](05-turns-and-results.md)

Thread 是多轮 Codex 会话的持久化边界。一个 Thread 可以包含多个 Turn，并持续积累会话上下文。

## 1. 创建 Thread

使用默认配置：

```java
CodexThread thread = codex.startThread();
```

指定项目和权限：

```java
CodexThread thread = codex.startThread(ThreadOptions.builder()
        .workingDirectory(Path.of("D:/code/my-project"))
        .sandbox(ThreadOptions.Sandbox.WORKSPACE_WRITE)
        .approvalPolicy(ThreadOptions.ApprovalPolicy.ON_REQUEST)
        .developerInstructions("遵循项目 AGENTS.md；修改后运行相关测试。")
        .build());
```

成功后立即保存 ID：

```java
String threadId = thread.id();
```

Thread ID 应被视为不透明字符串，不要解析其格式。

## 2. 恢复 Thread

```java
CodexThread resumed = codex.resumeThread(threadId);
TurnResult result = resumed.run("继续之前的任务。先总结当前进度。");
```

恢复时也可以覆盖部分配置：

```java
CodexThread resumed = codex.resumeThread(threadId, ThreadOptions.builder()
        .workingDirectory(Path.of("D:/code/my-project"))
        .sandbox(ThreadOptions.Sandbox.READ_ONLY)
        .build());
```

注意：`ThreadOptions` 同时服务于启动与恢复，但 `ephemeral` 是创建新 Thread 的语义。恢复时不要设置 `ephemeral`。

恢复失败的常见原因：

- Thread ID 不存在；
- 当前 app-server 使用了不同的 `CODEX_HOME`；
- 会话文件被归档或删除；
- CLI 版本无法读取旧会话；
- 当前用户没有会话目录权限。

## 3. 分叉 Thread

分叉会复制已有历史并生成新 ID，适合从同一上下文尝试两种实现方案：

```java
CodexThread original = codex.resumeThread(threadId);
CodexThread fork = codex.forkThread(original.id());

TurnResult conservative = original.run("给出风险最低的修复方案。");
TurnResult experimental = fork.run("尝试更彻底的重构方案。");
```

原 Thread 与分叉后的 Thread 后续互不影响。可以指定分叉边界和新 Thread 属性：

```java
CodexThread fork = codex.forkThread(threadId, ThreadOptions.builder()
        .lastTurnId(lastTurnId)
        .ephemeral(false)
        .threadSource("java-sdk")
        .build());
```

## 4. 列出 Thread

列出默认范围：

```java
ThreadListResponse response = codex.listThreads();

for (io.github.majiajustar.codex.generated.v2.Thread item : response.data()) {
    System.out.printf(
            "id=%s name=%s cwd=%s%n",
            item.id(),
            item.name(),
            item.cwd());
}
```

使用分页和筛选参数：

```java
ThreadListOptions options = ThreadListOptions.builder()
        .limit(20)
        .archived(false)
        .searchTerm("payment")
        .sortKey(ThreadSortKey.RECENCY_AT)
        .sortDirection(SortDirection.DESC)
        .sourceKinds(List.of(ThreadSourceKind.CLI, ThreadSourceKind.APP_SERVER))
        .build();

ThreadListResponse page = codex.listThreads(options);
String nextCursor = page.nextCursor();
```

继续下一页：

```java
if (nextCursor != null) {
    ThreadListResponse nextPage = codex.listThreads(ThreadListOptions.builder()
            .cursor(nextCursor)
            .limit(20)
            .archived(false)
            .build());
}
```

`ThreadListResponse.data()` 是
`List<io.github.majiajustar.codex.generated.v2.Thread>`，`nextCursor()` 用于向后翻页，
`backwardsCursor()` 用于反向翻页。需要尚未生成的实验字段时，仍可调用
`listThreads(JsonNode)` 原始重载。

## 5. 读取 Thread 与历史消息

推荐使用强类型历史 API：

```java
ThreadHistory history = thread.readHistory();

for (ThreadHistory.Turn turn : history.turns()) {
    System.out.printf("turn=%s status=%s%n", turn.id(), turn.status());

    for (CodexItem item : turn.items()) {
        switch (item) {
            case CodexItem.UserMessage message ->
                    System.out.println("用户：" + message.content());
            case CodexItem.AgentMessage message ->
                    System.out.println("Codex：" + message.text());
            case CodexItem.CommandExecution command ->
                    System.out.println("命令：" + command.command());
            case CodexItem.FileChange change ->
                    System.out.println("文件修改：" + change.changes());
            default -> System.out.println("Item：" + item.type());
        }
    }
}
```

`readHistory()` 会先读取 Thread 元数据，再根据 `historyMode` 自动选择读取策略：普通
历史使用 `thread/read(includeTurns=true)`，分页历史使用 `thread/turns/list`。如果旧版
app-server 未返回 `historyMode`，但普通读取返回明确的分页历史错误，SDK 也会自动回退到
分页读取。分页固定按时间正序请求完整 Item，直到 `nextCursor=null`。

每个 Item 会转换为 `CodexItem.UserMessage`、`CodexItem.AgentMessage`、
`CodexItem.CommandExecution`、`CodexItem.FileChange`、`CodexItem.McpToolCall` 等强类型。
未知 Item 会转换为 `CodexItem.Unknown`，完整协议字段可通过 `history.raw()`、
`turn.raw()` 和 `item.raw()` 获取。

应用重启后，如果只保存了 Thread ID，可以先恢复句柄再读取：

```java
CodexThread resumed = codex.resumeThread(threadId);
ThreadHistory history = resumed.readHistory();
```

原始 JSON 接口继续保留，适合尚未建模的新协议字段或只读取元数据的场景。

需要由界面自行控制分页时，可直接读取一页 Turn：

```java
ThreadTurnsPage page = thread.listTurns(ThreadTurnsListOptions.builder()
        .limit(100)
        .sortDirection(SortDirection.ASC)
        .itemsView(TurnItemsView.FULL)
        .build());

for (ThreadHistory.Turn turn : page.data()) {
    System.out.println(turn.id());
}

String nextCursor = page.nextCursor();
```

继续读取下一页时，将 `nextCursor` 传入 `.cursor(...)`。`limit` 的有效范围是 1 至 100；
省略排序方向时 app-server 默认按降序返回，省略 `itemsView` 时默认只返回摘要。

只读 Thread 元数据：

```java
JsonNode response = thread.read(false);
JsonNode threadData = response.path("thread");

System.out.println("ID: " + threadData.path("id").asText());
System.out.println("Status: " + threadData.path("status"));
```

读取原始 Turn 历史仅适合明确使用旧式历史的场景：

```java
JsonNode response = thread.read(true);
for (JsonNode turn : response.path("thread").path("turns")) {
    System.out.println(turn.path("id").asText() + " " + turn.path("status").asText());
}
```

分页 Thread 不应直接使用 `read(true)`；部分 app-server 版本会返回 `-32600`，其他版本也
可能只提供已弃用的兼容行为。一般业务代码应使用 `readHistory()` 或 `listTurns(...)`。
历史可能较大，只需检查状态或元数据时使用 `includeTurns=false`。

`thread.readHistory()` 返回的是 app-server 在分页期间可读取的持久 Thread 历史，不应
直接等同于“模型此刻看到的全部记忆”。长会话可能经过压缩，不同
历史模式或 CLI 版本能够还原的工具 Item 也可能不同；模型还可能拥有未作为普通聊天
消息展示的指令和上下文。

Web 应用通常应把原始响应映射成自己的 DTO，只向前端发送用户消息、代理消息和必要的
工具摘要。Solon 完整案例提供：

```http
GET /api/codex/history?sessionId=<网页会话ID>
```

网页 `sessionId` 只是当前服务进程内的映射键，真正持久的是响应中的 `threadId`。服务
重启后应使用该 `threadId` 调用 `resumeThread(...)`，创建新的网页会话后再读取历史。

## 6. 设置显示名称

```java
thread.setName("支付模块重构");
```

名称方便用户界面展示和搜索，不应当替代 Thread ID 作为数据库主键。

## 7. 压缩上下文

长会话可请求压缩历史：

```java
thread.compact();
```

该方法只表示 app-server 接受了压缩请求，实际进度可能通过标准 Turn/Item 通知继续发送。压缩会保留核心上下文，但不应假设每个早期细节都保持逐字可用。

## 8. 归档和取消归档

使用 Client：

```java
ThreadArchiveResponse archived = codex.archiveThread(thread.id());
ThreadUnarchiveResponse restored = codex.unarchiveThread(thread.id());
System.out.println(restored.thread().id());
```

也可以从 Thread 对象调用：

```java
thread.archive();
thread.unarchive();
```

归档通常是可恢复操作；删除则可能不可恢复。调用 `thread/delete` 前应增加业务确认和审计日志。

## 9. Thread 的并发规则

建议同一个 Thread 同一时间只启动一个普通 Turn。app-server 会维护活动 Turn 状态，并发执行可能返回“已有活动 Turn”或“expected active turn id”一类错误。

需要并行探索时：

1. 从同一 Thread 创建多个 fork；
2. 每个 fork 启动一个 Turn；
3. 在应用层比较结果；
4. 决定保留哪一条会话分支。

## 10. 生命周期存储建议

业务数据库至少记录：

```text
application_task_id
codex_thread_id
codex_home_identity
codex_cli_version
created_at
last_used_at
status
```

不要只保存 Thread ID 而忽略 `CODEX_HOME` 或运行环境身份，否则迁移机器后很难定位会话存储。

---

[上一章](03-configuration.md) · [返回目录](README.md) · [下一章：Turn 与结果](05-turns-and-results.md)
