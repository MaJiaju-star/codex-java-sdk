package io.github.majiajustar.codex.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.majiajustar.codex.CodexClientConfig;
import io.github.majiajustar.codex.internal.McpConfigOverrideSerializer;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class McpServerConfigTest {
    @Test
    void serializesCompleteStdioConfiguration() {
        var cwd = Path.of("workspace", "mcp");
        var config = McpServerConfig.stdio("npx")
                .args("-y", "@modelcontextprotocol/server-filesystem")
                .env("LOG_LEVEL", "debug")
                .envVar(McpEnvVar.inherit("HOME"))
                .envVar(McpEnvVar.remote("REMOTE_TOKEN"))
                .cwd(cwd)
                .enabled(false)
                .required(true)
                .startupTimeout(Duration.ofMillis(1500))
                .toolTimeout(Duration.ofSeconds(60))
                .supportsParallelToolCalls(true)
                .enabledTools()
                .disabledTools("delete")
                .defaultToolsApprovalMode(McpToolApprovalMode.PROMPT)
                .tool("read", McpToolConfig.approval(McpToolApprovalMode.APPROVE))
                .build();

        var escapedCwd = cwd.toString().replace("\\", "\\\\");
        assertEquals(
                "mcp_servers.file-system={command=\"npx\","
                        + "args=[\"-y\",\"@modelcontextprotocol/server-filesystem\"],"
                        + "env={\"LOG_LEVEL\"=\"debug\"},"
                        + "env_vars=[\"HOME\",{name=\"REMOTE_TOKEN\",source=\"remote\"}],"
                        + "cwd=\"" + escapedCwd + "\",enabled=false,required=true,"
                        + "startup_timeout_sec=1.5,tool_timeout_sec=60,"
                        + "supports_parallel_tool_calls=true,enabled_tools=[],"
                        + "disabled_tools=[\"delete\"],default_tools_approval_mode=\"prompt\","
                        + "tools={\"read\"={approval_mode=\"approve\"}}}",
                McpConfigOverrideSerializer.serialize("file-system", config));
    }

    @Test
    void serializesCompleteHttpConfigurationWithoutExposingTokenValue() {
        var config = McpServerConfig.streamableHttp(URI.create("https://mcp.example.com/api"))
                .bearerTokenEnvVar("MCP_TOKEN")
                .httpHeader("X-Tenant", "internal")
                .envHttpHeader("Authorization", "MCP_AUTH_HEADER")
                .httpHeadersHelper("resolve-headers")
                .auth(McpAuthMode.OAUTH)
                .oauth(McpOAuthConfig.builder()
                        .clientId("java-sdk")
                        .callbackUrl(URI.create("http://127.0.0.1:8765/callback"))
                        .callbackPort(8765)
                        .build())
                .environmentId("local")
                .omitToolsFrom(McpToolExposureSurface.CODE_MODE, McpToolExposureSurface.DEFERRED)
                .scopes("files.read", "files.write")
                .oauthResource("https://mcp.example.com")
                .build();

        var serialized = McpConfigOverrideSerializer.serialize("internal", config);
        assertEquals(
                "mcp_servers.internal={url=\"https://mcp.example.com/api\","
                        + "bearer_token_env_var=\"MCP_TOKEN\","
                        + "http_headers={\"X-Tenant\"=\"internal\"},"
                        + "env_http_headers={\"Authorization\"=\"MCP_AUTH_HEADER\"},"
                        + "http_headers_helper=\"resolve-headers\",auth=\"oauth\","
                        + "oauth={client_id=\"java-sdk\","
                        + "callback_url=\"http://127.0.0.1:8765/callback\",callback_port=8765},"
                        + "enabled=true,environment_id=\"local\","
                        + "omit_tools_from=[\"code_mode\",\"deferred\"],"
                        + "scopes=[\"files.read\",\"files.write\"],"
                        + "oauth_resource=\"https://mcp.example.com\"}",
                serialized);
    }

    @Test
    void typedMcpRegistrationFollowsRawOverridesAndKeepsSecretsInEnvironment() {
        var server = McpServerConfig.streamableHttp(URI.create("https://mcp.example.com"))
                .bearerTokenEnvVar("MCP_TOKEN")
                .build();
        var config = CodexClientConfig.builder()
                .command(List.of("codex", "app-server"))
                .configOverride("mcp_servers.docs={url=\"https://old.example.com\"}")
                .environment("MCP_TOKEN", "actual-secret")
                .mcpServer("docs", server)
                .build();

        assertEquals(
                List.of(
                        "codex",
                        "--config",
                        "mcp_servers.docs={url=\"https://old.example.com\"}",
                        "--config",
                        "mcp_servers.docs={url=\"https://mcp.example.com\","
                                + "bearer_token_env_var=\"MCP_TOKEN\",enabled=true}",
                        "app-server"),
                config.command());
        assertEquals("actual-secret", config.environment().get("MCP_TOKEN"));
        assertFalse(String.join(" ", config.command()).contains("actual-secret"));
    }

    @Test
    void validatesTransportAndCommonFields() {
        assertThrows(
                IllegalArgumentException.class,
                () -> McpServerConfig.streamableHttp(URI.create("file:///tmp/mcp")).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> McpServerConfig.stdio("command")
                        .startupTimeout(Duration.ofSeconds(-1))
                        .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> McpOAuthConfig.builder().callbackPort(65536).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> McpOAuthConfig.builder().callbackUrl(URI.create("/callback")).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> CodexClientConfig.builder()
                        .mcpServer(" ", McpServerConfig.stdio("command").build()));
        assertThrows(
                IllegalArgumentException.class,
                () -> CodexClientConfig.builder()
                        .mcpServer("invalid.name", McpServerConfig.stdio("command").build()));
    }
}
