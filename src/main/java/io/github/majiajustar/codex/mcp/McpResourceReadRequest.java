package io.github.majiajustar.codex.mcp;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.net.URI;
import java.util.Objects;

/** 读取 MCP Server 资源所需的参数。 */
public record McpResourceReadRequest(String server, URI uri, String threadId) {
    public McpResourceReadRequest {
        if (Objects.requireNonNull(server, "server").isBlank()) {
            throw new IllegalArgumentException("server must not be blank");
        }
        Objects.requireNonNull(uri, "uri");
    }

    /** 创建不绑定线程的资源读取请求。 */
    public static McpResourceReadRequest create(String server, URI uri) {
        return new McpResourceReadRequest(server, uri, null);
    }

    ObjectNode toJson() {
        var json = JsonSupport.MAPPER.createObjectNode()
                .put("server", server)
                .put("uri", uri.toString());
        if (threadId != null) json.put("threadId", threadId);
        return json;
    }
}
