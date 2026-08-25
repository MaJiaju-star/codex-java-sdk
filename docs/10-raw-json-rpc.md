# 10. 使用原始 JSON-RPC 扩展 SDK

[上一章](09-async-and-concurrency.md) · [返回目录](README.md) · [下一章：异常与排查](11-errors-and-troubleshooting.md)

app-server v2 的 API 数量远多于 Java SDK 当前的便捷方法。`CodexClient.request(...)` 和 `requestAsync(...)` 允许直接调用任意兼容方法。

## 1. 基本形式

同步：

```java
JsonNode response = codex.request("method/name", params);
```

异步：

```java
CompletableFuture<JsonNode> response = codex.requestAsync("method/name", params);
```

SDK 自动完成以下工作：

- 生成唯一请求 ID；
- 将 `method` 和 `params` 编码为单行 JSON；
- 串行化 stdin 写入，避免消息交叉；
- 从 stdout 路由对应响应；
- 将 JSON-RPC error 转换为 `JsonRpcException`。

## 2. Jackson 构造参数

```java
ObjectMapper mapper = new ObjectMapper();
ObjectNode params = mapper.createObjectNode();
params.put("threadId", threadId);
params.put("includeTurns", true);

JsonNode response = codex.request("thread/read", params);
```

嵌套对象：

```java
ObjectNode params = mapper.createObjectNode();
params.put("threadId", threadId);

ObjectNode metadata = params.putObject("metadata");
metadata.put("source", "java-service");
metadata.put("ticket", "DEV-1234");
```

数组：

```java
ArrayNode input = params.putArray("input");
input.addObject()
        .put("type", "text")
        .put("text", "继续执行任务");
```

## 3. 归档 Thread

归档和取消归档已经有强类型便捷 API，业务代码应优先使用：

```java
ThreadArchiveResponse archived = codex.archiveThread(threadId);
ThreadUnarchiveResponse restored = codex.unarchiveThread(threadId);
String restoredId = restored.thread().id();
```

下面的原始写法仅用于说明同一操作对应的 JSON-RPC 结构，或兼容尚未进入当前生成层的
实验字段：

```java
JsonNode response = codex.request(
        "thread/unarchive",
        mapper.createObjectNode().put("threadId", threadId));

String restoredId = requiredText(response, "thread", "id");
```

辅助方法：

```java
static String requiredText(JsonNode root, String parent, String field) {
    JsonNode value = root.path(parent).path(field);
    if (!value.isTextual()) {
        throw new IllegalStateException("Missing " + parent + "." + field);
    }
    return value.asText();
}
```

## 4. 高级 Thread 列表

```java
ObjectNode params = mapper.createObjectNode()
        .put("limit", 50)
        .put("archived", false)
        .put("cwd", "D:/code/my-project")
        .put("searchTerm", "authentication");

JsonNode response = codex.request("thread/list", params);

for (JsonNode item : response.path("data")) {
    System.out.println(item.path("id").asText());
}
```

分页：

```java
String cursor = null;

do {
    ObjectNode params = mapper.createObjectNode().put("limit", 50);
    if (cursor != null) {
        params.put("cursor", cursor);
    }

    JsonNode page = codex.request("thread/list", params);
    process(page.path("data"));

    JsonNode next = page.path("nextCursor");
    cursor = next.isTextual() ? next.asText() : null;
} while (cursor != null);
```

`process(...)` 是示例应用自行实现的方法。

## 5. 原始 `turn/steer`

便捷 `CodexTurn.steer(...)` 只接受字符串。多输入项可以直接调用：

```java
ObjectNode params = mapper.createObjectNode()
        .put("threadId", threadId)
        .put("expectedTurnId", turnId);

ArrayNode input = params.putArray("input");
input.addObject()
        .put("type", "text")
        .put("text", "结合这张截图继续分析");
input.addObject()
        .put("type", "localImage")
        .put("path", "D:/artifacts/failure.png");

JsonNode response = codex.request("turn/steer", params);
```

## 6. 查询模型

已有便捷方法：

```java
JsonNode response = codex.models(false);
```

等价原始请求：

```java
JsonNode response = codex.request(
        "model/list",
        mapper.createObjectNode().put("includeHidden", false));
```

解析时不要假设所有模型都有相同可选字段：

```java
for (JsonNode model : response.path("data")) {
    String id = model.path("id").asText();
    String displayName = model.path("displayName").asText(id);
    System.out.printf("%s (%s)%n", displayName, id);
}
```

## 7. 原始响应映射为 record

对于业务中频繁使用且结构稳定的方法，可以在应用层定义 DTO：

```java
record ThreadSummary(String id, String name, String cwd) {}

List<ThreadSummary> parseThreads(ObjectMapper mapper, JsonNode response) {
    ArrayList<ThreadSummary> result = new ArrayList<>();
    for (JsonNode node : response.path("data")) {
        result.add(new ThreadSummary(
                node.path("id").asText(),
                node.path("name").asText(null),
                node.path("cwd").asText(null)));
    }
    return List.copyOf(result);
}
```

建议保留原始 `JsonNode` 或未知字段，以便升级协议时调试。

## 8. 如何获得准确 Schema

应从实际使用的 Codex CLI 生成协议定义：

```shell
codex app-server generate-json-schema --out generated-schema
codex app-server generate-ts --out generated-ts
```

不同 CLI 版本支持的命令参数可能略有差异，请先运行：

```shell
codex app-server --help
```

生成的 Schema 与该 CLI 版本匹配，是编写原始调用最可靠的依据。

## 9. 实验 API

部分方法或字段要求初始化能力 `experimentalApi=true`，它在 `CodexClientConfig` 中默认开启：

```java
CodexClientConfig config = CodexClientConfig.builder()
        .experimentalApi(true)
        .build();
```

实验接口可能更快变化。调用方应：

- 固定 CLI 版本；
- 对缺失字段提供默认处理；
- 捕获 `JsonRpcException`；
- 在升级前执行契约测试。

## 10. 不要绕过 Client 直接写 stdin

`CodexClient` 必须是 stdout 的唯一读取者和 stdin 的统一写入者。应用自己读写子进程流会破坏请求响应路由、审批响应和 Turn 事件缓存。

需要扩展时始终使用公开的 `request(...)`、`requestAsync(...)` 和事件接口。

---

[上一章](09-async-and-concurrency.md) · [返回目录](README.md) · [下一章：异常与排查](11-errors-and-troubleshooting.md)
