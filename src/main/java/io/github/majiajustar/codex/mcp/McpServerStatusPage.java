package io.github.majiajustar.codex.mcp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.util.List;
import java.util.Map;

/** 一页 MCP Server 状态和下一页游标。 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpServerStatusPage(
        List<McpServerStatus> data, String nextCursor, JsonNode raw) {
    public McpServerStatusPage {
        data = List.copyOf(data);
    }

    static McpServerStatusPage from(JsonNode value) {
        var decoded = JsonSupport.MAPPER.convertValue(value, WirePage.class);
        return new McpServerStatusPage(decoded.data(), decoded.nextCursor(), value);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WirePage(List<McpServerStatus> data, String nextCursor) {}

    /** 单个 MCP Server 的连接、认证和资源清单。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record McpServerStatus(
            String name,
            ConnectionStatus runtimeStatus,
            String pluginId,
            ServerInfo serverInfo,
            Map<String, Tool> tools,
            List<Resource> resources,
            List<ResourceTemplate> resourceTemplates,
            AuthStatus authStatus) {
        public McpServerStatus {
            tools = Map.copyOf(tools);
            resources = List.copyOf(resources);
            resourceTemplates = List.copyOf(resourceTemplates);
        }
    }

    /** MCP Server 公布的展示信息。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ServerInfo(
            String name,
            String title,
            String version,
            String description,
            List<JsonNode> icons,
            String websiteUrl) {}

    /** MCP Server 公布的工具定义。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tool(
            String name,
            String title,
            String description,
            JsonNode inputSchema,
            JsonNode outputSchema,
            JsonNode annotations,
            List<JsonNode> icons,
            JsonNode _meta) {}

    /** MCP Server 公布的可读取资源。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resource(
            String uri,
            String name,
            String title,
            String description,
            String mimeType,
            Long size,
            List<JsonNode> icons,
            JsonNode annotations,
            JsonNode _meta) {}

    /** MCP Server 公布的资源模板。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResourceTemplate(
            String uriTemplate,
            String name,
            String title,
            String description,
            String mimeType,
            JsonNode annotations) {}

    /** MCP Server 当前运行时连接状态。 */
    public enum ConnectionStatus {
        NOT_STARTED("notStarted"),
        STARTING("starting"),
        CONNECTED("connected"),
        AUTHENTICATION_REQUIRED("authenticationRequired"),
        FAILED("failed"),
        CANCELLED("cancelled"),
        DISABLED("disabled"),
        UNKNOWN(null);

        private final String wireValue;

        ConnectionStatus(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonCreator
        public static ConnectionStatus fromWireValue(String value) {
            for (var status : values()) {
                if (status.wireValue != null && status.wireValue.equals(value)) return status;
            }
            return UNKNOWN;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }
    }

    /** MCP Server 当前使用的认证方式。 */
    public enum AuthStatus {
        UNKNOWN("unknown"),
        UNSUPPORTED("unsupported"),
        NOT_LOGGED_IN("notLoggedIn"),
        BEARER_TOKEN("bearerToken"),
        OAUTH("oAuth");

        private final String wireValue;

        AuthStatus(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonCreator
        public static AuthStatus fromWireValue(String value) {
            for (var status : values()) {
                if (status.wireValue.equals(value)) return status;
            }
            return UNKNOWN;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }
    }
}
