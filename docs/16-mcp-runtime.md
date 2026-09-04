# 16. MCP 运行时 API

[上一章](15-schema-code-generation.md) · [返回目录](README.md)

`CodexClient.mcp()` 返回绑定到当前 app-server 连接的 `McpClient`。启动配置仍由
`CodexClientConfig.mcpServer(...)` 管理；本章 API 用于连接建立后的查询和操作。

## 查询 Server 状态

```java
McpServerStatusPage page = codex.mcp().listStatuses();

for (McpServerStatusPage.McpServerStatus server : page.data()) {
    System.out.println(server.name() + ": " + server.runtimeStatus());
    server.tools().values().forEach(tool -> System.out.println(tool.name()));
}
```

需要分页、减少资源清单开销或查询特定 Thread 时，传入 `McpStatusListOptions`：

```java
var options = McpStatusListOptions.builder()
        .limit(50)
        .detail(McpStatusListOptions.Detail.TOOLS_AND_AUTH_ONLY)
        .threadId(thread.id())
        .build();

McpServerStatusPage page = codex.mcp().listStatuses(options);
```

`McpServerStatusPage` 强类型表示连接状态、认证状态、Server 信息、工具、资源和资源模板。

## 重新加载配置

```java
codex.mcp().reload();
```

该方法调用 `config/mcpServer/reload`。它重新读取 Codex 当前配置；不会修改 Java
`CodexClientConfig`，也不会把新的启动时 Builder 配置写入正在运行的进程。

## OAuth 登录

```java
var request = McpOAuthLoginRequest.builder("internal")
        .scopes("files.read")
        .threadId(thread.id())
        .timeout(Duration.ofSeconds(120))
        .build();

McpOAuthLoginResult login = codex.mcp().startOAuthLogin(request);
URI authorizationUrl = login.authorizationUrl();
```

应用应在浏览器中打开授权地址，并监听客户端全局事件中的
`CodexNotification.McpServerOAuthLoginCompleted`。Server 启动状态变化对应
`CodexNotification.McpServerStatusUpdated`。

## 读取资源

```java
var request = McpResourceReadRequest.create(
        "docs",
        URI.create("docs://guide/getting-started"));
McpResourceReadResult result = codex.mcp().readResource(request);

for (McpResourceReadResult.Content content : result.contents()) {
    switch (content) {
        case McpResourceReadResult.Text text -> System.out.println(text.text());
        case McpResourceReadResult.Blob blob -> System.out.println(blob.mimeType());
        case McpResourceReadResult.Unknown unknown -> System.out.println(unknown.raw());
    }
}
```

## 直接调用工具

```java
ObjectNode arguments = mapper.createObjectNode().put("query", "Java SDK");
var request = McpToolCallRequest.create(
        thread.id(), "docs", "search", arguments);
McpToolCallResponse response = codex.mcp().callTool(request);

if (!Boolean.TRUE.equals(response.isError())) {
    System.out.println(response.structuredContent());
}
```

直接调用必须携带 Thread ID。普通 Agent Turn 中由模型发起的 MCP 调用仍通过
`CodexItem.McpToolCall` 和 `CodexNotification.McpToolCallProgress` 观察。

## 安全建议

- Bearer token 通过 `bearerTokenEnvVar` 和子进程环境传递，不要写进源码或参数。
- `httpHeadersHelper` 是本地命令，避免把密钥直接放在命令文本中。
- 直接工具调用绕过模型选择步骤，调用前应自行完成租户、Thread 和工具权限校验。
- Web 服务应把 OAuth 完成通知按 Thread 或用户会话路由，避免跨用户泄露状态。

---

[上一章：Schema 代码生成](15-schema-code-generation.md) · [返回目录](README.md) ·
[下一章：Goal、Skills 与 Config API](17-goals-skills-config.md)
