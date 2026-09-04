package io.github.majiajustar.codex.mcp;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.majiajustar.codex.internal.JsonSupport;

/** MCP Server 状态列表的分页和详情选项。 */
public record McpStatusListOptions(String cursor, Integer limit, Detail detail, String threadId) {
    public McpStatusListOptions {
        if (limit != null && limit < 0) throw new IllegalArgumentException("limit must not be negative");
    }

    /** 使用服务器默认分页和详情设置。 */
    public static McpStatusListOptions defaults() {
        return new McpStatusListOptions(null, null, null, null);
    }

    /** 创建状态列表选项 Builder。 */
    public static Builder builder() {
        return new Builder();
    }

    ObjectNode toJson() {
        var json = JsonSupport.MAPPER.createObjectNode();
        if (cursor != null) json.put("cursor", cursor);
        if (limit != null) json.put("limit", limit);
        if (detail != null) json.put("detail", detail.wireValue);
        if (threadId != null) json.put("threadId", threadId);
        return json;
    }

    /** 控制是否读取每个 Server 的完整资源清单。 */
    public enum Detail {
        FULL("full"),
        TOOLS_AND_AUTH_ONLY("toolsAndAuthOnly");

        private final String wireValue;

        Detail(String wireValue) {
            this.wireValue = wireValue;
        }
    }

    /** 构建可选分页和 Thread 上下文。 */
    public static final class Builder {
        private String cursor;
        private Integer limit;
        private Detail detail;
        private String threadId;

        public Builder cursor(String cursor) {
            this.cursor = cursor;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder detail(Detail detail) {
            this.detail = detail;
            return this;
        }

        public Builder threadId(String threadId) {
            this.threadId = threadId;
            return this;
        }

        public McpStatusListOptions build() {
            return new McpStatusListOptions(cursor, limit, detail, threadId);
        }
    }
}
