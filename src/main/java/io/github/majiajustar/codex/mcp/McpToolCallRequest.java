package io.github.majiajustar.codex.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.util.Objects;

/** 直接调用线程中的 MCP 工具所需的参数。 */
public record McpToolCallRequest(
        String threadId, String server, String tool, JsonNode arguments, JsonNode metadata) {
    public McpToolCallRequest {
        requireText(threadId, "threadId");
        requireText(server, "server");
        requireText(tool, "tool");
    }

    /** 创建不携带 MCP 元数据的工具调用。 */
    public static McpToolCallRequest create(
            String threadId, String server, String tool, JsonNode arguments) {
        return new McpToolCallRequest(threadId, server, tool, arguments, null);
    }

    ObjectNode toJson() {
        var json = JsonSupport.MAPPER.createObjectNode()
                .put("threadId", threadId)
                .put("server", server)
                .put("tool", tool);
        if (arguments != null) json.set("arguments", arguments);
        if (metadata != null) json.set("_meta", metadata);
        return json;
    }

    private static void requireText(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
