package io.github.majiajustar.codex.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/** MCP 资源读取结果。 */
public record McpResourceReadResult(List<Content> contents, String originCallId, JsonNode raw) {
    public McpResourceReadResult {
        contents = List.copyOf(contents);
    }

    static McpResourceReadResult from(JsonNode value) {
        var contents = new ArrayList<Content>();
        value.path("contents").forEach(item -> {
            var uri = item.path("uri").asText();
            var mimeType = nullableText(item, "mimeType");
            var metadata = item.get("_meta");
            if (item.has("text")) {
                contents.add(new Text(uri, mimeType, item.path("text").asText(), metadata, item));
            } else if (item.has("blob")) {
                contents.add(new Blob(uri, mimeType, item.path("blob").asText(), metadata, item));
            } else {
                contents.add(new Unknown(uri, mimeType, metadata, item));
            }
        });
        return new McpResourceReadResult(contents, nullableText(value, "originCallId"), value);
    }

    /** MCP 资源内容的强类型视图。 */
    public sealed interface Content permits Text, Blob, Unknown {
        String uri();

        String mimeType();

        JsonNode metadata();

        JsonNode raw();
    }

    /** UTF-8 文本资源内容。 */
    public record Text(String uri, String mimeType, String text, JsonNode metadata, JsonNode raw)
            implements Content {}

    /** Base64 编码的二进制资源内容。 */
    public record Blob(String uri, String mimeType, String blob, JsonNode metadata, JsonNode raw)
            implements Content {}

    /** 为协议未来新增内容类型保留的原始资源。 */
    public record Unknown(String uri, String mimeType, JsonNode metadata, JsonNode raw)
            implements Content {}

    private static String nullableText(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
