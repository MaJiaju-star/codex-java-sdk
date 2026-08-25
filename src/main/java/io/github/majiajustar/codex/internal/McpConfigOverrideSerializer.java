package io.github.majiajustar.codex.internal;

import io.github.majiajustar.codex.mcp.McpEnvVar;
import io.github.majiajustar.codex.mcp.McpOAuthConfig;
import io.github.majiajustar.codex.mcp.McpServerConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** 将强类型 MCP 配置编码为 Codex CLI 接受的 TOML 配置覆盖。 */
public final class McpConfigOverrideSerializer {
    private static final Pattern SERVER_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private McpConfigOverrideSerializer() {}

    /** 序列化一个具名 MCP Server。 */
    public static String serialize(String name, McpServerConfig config) {
        validateName(name);
        Objects.requireNonNull(config, "config");

        var fields = new ArrayList<String>();
        switch (config.transport()) {
            case McpServerConfig.Stdio stdio -> addStdio(fields, stdio);
            case McpServerConfig.StreamableHttp http -> addHttp(fields, http);
        }
        fields.add("enabled=" + config.enabled());
        if (config.required()) fields.add("required=true");
        if (config.startupTimeout() != null) {
            fields.add("startup_timeout_sec=" + seconds(config.startupTimeout()));
        }
        if (config.toolTimeout() != null) {
            fields.add("tool_timeout_sec=" + seconds(config.toolTimeout()));
        }
        if (config.supportsParallelToolCalls()) {
            fields.add("supports_parallel_tool_calls=true");
        }
        if (config.enabledTools() != null) {
            fields.add("enabled_tools=" + stringArray(config.enabledTools()));
        }
        if (config.disabledTools() != null) {
            fields.add("disabled_tools=" + stringArray(config.disabledTools()));
        }
        if (config.defaultToolsApprovalMode() != null) {
            fields.add("default_tools_approval_mode="
                    + string(config.defaultToolsApprovalMode().wireValue()));
        }
        if (!config.tools().isEmpty()) fields.add("tools=" + tools(config.tools()));
        return "mcp_servers." + name + "={" + String.join(",", fields) + "}";
    }

    /** 校验 MCP Server 名称是否能安全用作 CLI dotted override 的路径段。 */
    public static void validateName(String name) {
        if (!SERVER_NAME.matcher(Objects.requireNonNull(name, "name")).matches()) {
            throw new IllegalArgumentException(
                    "MCP server name must contain only letters, digits, '_' or '-'");
        }
    }

    private static void addStdio(List<String> fields, McpServerConfig.Stdio stdio) {
        fields.add("command=" + string(stdio.command()));
        if (!stdio.args().isEmpty()) fields.add("args=" + stringArray(stdio.args()));
        if (!stdio.env().isEmpty()) fields.add("env=" + stringMap(stdio.env()));
        if (!stdio.envVars().isEmpty()) fields.add("env_vars=" + envVars(stdio.envVars()));
        if (stdio.cwd() != null) fields.add("cwd=" + string(stdio.cwd().toString()));
    }

    private static void addHttp(List<String> fields, McpServerConfig.StreamableHttp http) {
        fields.add("url=" + string(http.url().toString()));
        if (http.bearerTokenEnvVar() != null) {
            fields.add("bearer_token_env_var=" + string(http.bearerTokenEnvVar()));
        }
        if (!http.httpHeaders().isEmpty()) {
            fields.add("http_headers=" + stringMap(http.httpHeaders()));
        }
        if (!http.envHttpHeaders().isEmpty()) {
            fields.add("env_http_headers=" + stringMap(http.envHttpHeaders()));
        }
        if (http.auth() != null) fields.add("auth=" + string(http.auth().wireValue()));
        if (http.oauth() != null) fields.add("oauth=" + oauth(http.oauth()));
    }

    private static String envVars(List<McpEnvVar> values) {
        return "[" + values.stream()
                .map(value -> value.source() == null
                        ? string(value.name())
                        : "{name=" + string(value.name()) + ",source="
                                + string(value.source().wireValue()) + "}")
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private static String oauth(McpOAuthConfig oauth) {
        var fields = new ArrayList<String>();
        if (oauth.clientId() != null) fields.add("client_id=" + string(oauth.clientId()));
        if (oauth.callbackPort() != null) fields.add("callback_port=" + oauth.callbackPort());
        return "{" + String.join(",", fields) + "}";
    }

    private static String tools(Map<String, io.github.majiajustar.codex.mcp.McpToolConfig> tools) {
        var fields = new ArrayList<String>();
        tools.forEach((name, config) -> fields.add(key(name) + "={approval_mode="
                + string(config.approvalMode().wireValue()) + "}"));
        return "{" + String.join(",", fields) + "}";
    }

    private static String stringMap(Map<String, String> values) {
        var fields = new ArrayList<String>();
        values.forEach((name, value) -> fields.add(key(name) + "=" + string(value)));
        return "{" + String.join(",", fields) + "}";
    }

    private static String stringArray(List<String> values) {
        return "[" + values.stream().map(McpConfigOverrideSerializer::string).reduce(
                        (left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private static String seconds(Duration duration) {
        return BigDecimal.valueOf(duration.getSeconds())
                .add(BigDecimal.valueOf(duration.getNano(), 9))
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String key(String value) {
        return string(value);
    }

    private static String string(String value) {
        var result = new StringBuilder(value.length() + 2).append('"');
        value.codePoints().forEach(character -> {
            switch (character) {
                case '\b' -> result.append("\\b");
                case '\t' -> result.append("\\t");
                case '\n' -> result.append("\\n");
                case '\f' -> result.append("\\f");
                case '\r' -> result.append("\\r");
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                default -> {
                    if (character < 0x20) result.append(String.format("\\u%04X", character));
                    else result.appendCodePoint(character);
                }
            }
        });
        return result.append('"').toString();
    }
}
