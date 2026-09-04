package io.github.majiajustar.codex.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;

/** MCP OAuth 登录启动结果。 */
public record McpOAuthLoginResult(URI authorizationUrl, JsonNode raw) {
    static McpOAuthLoginResult from(JsonNode value) {
        return new McpOAuthLoginResult(
                URI.create(value.path("authorizationUrl").asText()), value);
    }
}
