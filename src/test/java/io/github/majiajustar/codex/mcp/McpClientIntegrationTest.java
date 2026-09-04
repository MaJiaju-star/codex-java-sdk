package io.github.majiajustar.codex.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.majiajustar.codex.CodexClient;
import io.github.majiajustar.codex.CodexClientConfig;
import io.github.majiajustar.codex.MockAppServer;
import io.github.majiajustar.codex.event.CodexEvent;
import io.github.majiajustar.codex.event.CodexEventType;
import io.github.majiajustar.codex.event.CodexNotification;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class McpClientIntegrationTest {
    @Test
    void managesMcpServersThroughTypedRuntimeApi() throws Exception {
        var config = CodexClientConfig.builder()
                .command(mockServerCommand())
                .requestTimeout(Duration.ofSeconds(10))
                .build();

        try (var codex = CodexClient.create(config)) {
            var statuses = codex.mcp().listStatuses();
            assertEquals(1, statuses.data().size());
            var status = statuses.data().getFirst();
            assertEquals("docs", status.name());
            assertEquals(McpServerStatusPage.ConnectionStatus.CONNECTED, status.runtimeStatus());
            assertEquals(McpServerStatusPage.AuthStatus.BEARER_TOKEN, status.authStatus());
            assertEquals("search", status.tools().get("search").name());

            codex.mcp().reload();
            assertEquals(
                    URI.create("https://auth.example.com/authorize"),
                    codex.mcp()
                            .startOAuthLogin(McpOAuthLoginRequest.forServer("docs"))
                            .authorizationUrl());

            var resource = codex.mcp().readResource(
                    McpResourceReadRequest.create("docs", URI.create("docs://guide")));
            assertEquals("call-1", resource.originCallId());
            assertEquals("guide text", assertInstanceOf(
                            McpResourceReadResult.Text.class, resource.contents().getFirst())
                    .text());
            assertInstanceOf(McpResourceReadResult.Blob.class, resource.contents().get(1));

            var arguments = JsonSupport.MAPPER.createObjectNode().put("query", "java");
            var tool = codex.mcp().callTool(
                    McpToolCallRequest.create("thread-1", "docs", "search", arguments));
            assertFalse(tool.isError());
            assertEquals(1, tool.structuredContent().path("matches").asInt());
        }
    }

    @Test
    void parsesMcpLifecycleNotifications() throws Exception {
        var statusParams = JsonSupport.MAPPER.readTree("""
                {
                  "threadId":"thread-1",
                  "name":"docs",
                  "status":"ready",
                  "error":null,
                  "failureReason":null
                }
                """);
        var statusEvent = new CodexEvent("mcpServer/startupStatus/updated", statusParams);
        var status = assertInstanceOf(
                CodexNotification.McpServerStatusUpdated.class, statusEvent.notification());
        assertEquals(CodexEventType.MCP_SERVER_STATUS_UPDATED, statusEvent.type());
        assertEquals(McpServerStartupState.READY, status.status());

        var oauthParams = JsonSupport.MAPPER.readTree("""
                {"name":"docs","threadId":null,"success":true}
                """);
        var oauthEvent = new CodexEvent("mcpServer/oauthLogin/completed", oauthParams);
        var oauth = assertInstanceOf(
                CodexNotification.McpServerOAuthLoginCompleted.class,
                oauthEvent.notification());
        assertEquals(CodexEventType.MCP_SERVER_OAUTH_LOGIN_COMPLETED, oauthEvent.type());
        assertEquals(true, oauth.successful());
    }

    private static List<String> mockServerCommand() {
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return List.of(java, "-cp", System.getProperty("java.class.path"), MockAppServer.class.getName());
    }
}
