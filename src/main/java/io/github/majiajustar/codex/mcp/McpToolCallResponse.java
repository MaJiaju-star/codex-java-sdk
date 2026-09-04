package io.github.majiajustar.codex.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/** 直接调用 MCP 工具后返回的内容。 */
public record McpToolCallResponse(
        List<JsonNode> content,
        JsonNode structuredContent,
        Boolean isError,
        JsonNode metadata,
        JsonNode raw) {
    public McpToolCallResponse {
        content = List.copyOf(content);
    }

    static McpToolCallResponse from(JsonNode value) {
        var content = new ArrayList<JsonNode>();
        value.path("content").forEach(content::add);
        var isError = value.has("isError") ? value.path("isError").asBoolean() : null;
        return new McpToolCallResponse(
                content, value.get("structuredContent"), isError, value.get("_meta"), value);
    }
}
