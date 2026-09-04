package io.github.majiajustar.codex.mcp;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 一个由 Codex app-server 初始化并向模型暴露工具的 MCP Server 配置。 */
public final class McpServerConfig {
    private final Transport transport;
    private final boolean enabled;
    private final boolean required;
    private final Duration startupTimeout;
    private final Duration toolTimeout;
    private final boolean supportsParallelToolCalls;
    private final String environmentId;
    private final List<McpToolExposureSurface> omitToolsFrom;
    private final List<String> enabledTools;
    private final List<String> disabledTools;
    private final List<String> scopes;
    private final String oauthResource;
    private final McpToolApprovalMode defaultToolsApprovalMode;
    private final Map<String, McpToolConfig> tools;

    private McpServerConfig(Builder<?> builder, Transport transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
        enabled = builder.enabled;
        required = builder.required;
        startupTimeout = nonNegative(builder.startupTimeout, "startupTimeout");
        toolTimeout = nonNegative(builder.toolTimeout, "toolTimeout");
        supportsParallelToolCalls = builder.supportsParallelToolCalls;
        environmentId = builder.environmentId;
        omitToolsFrom = builder.omitToolsFrom == null ? null : List.copyOf(builder.omitToolsFrom);
        enabledTools = copyOptionalNames(builder.enabledTools, "enabledTools");
        disabledTools = copyOptionalNames(builder.disabledTools, "disabledTools");
        scopes = copyOptionalNames(builder.scopes, "scopes");
        oauthResource = builder.oauthResource;
        defaultToolsApprovalMode = builder.defaultToolsApprovalMode;
        tools = immutableMap(builder.tools);
    }

    /** 创建 stdio MCP Server Builder。 */
    public static StdioBuilder stdio(String command) {
        return new StdioBuilder(command);
    }

    /** 创建 Streamable HTTP MCP Server Builder。 */
    public static HttpBuilder streamableHttp(URI url) {
        return new HttpBuilder(url);
    }

    public Transport transport() {
        return transport;
    }
    public boolean enabled() {
        return enabled;
    }
    public boolean required() {
        return required;
    }
    public Duration startupTimeout() {
        return startupTimeout;
    }
    public Duration toolTimeout() {
        return toolTimeout;
    }
    public boolean supportsParallelToolCalls() {
        return supportsParallelToolCalls;
    }
    public String environmentId() {
        return environmentId;
    }
    public List<McpToolExposureSurface> omitToolsFrom() {
        return omitToolsFrom;
    }

    /** 返回工具白名单；未配置时返回 {@code null}，空列表表示不暴露任何工具。 */
    public List<String> enabledTools() {
        return enabledTools;
    }
    /** 返回工具黑名单；未配置时返回 {@code null}。 */
    public List<String> disabledTools() {
        return disabledTools;
    }
    public List<String> scopes() {
        return scopes;
    }
    public String oauthResource() {
        return oauthResource;
    }
    public McpToolApprovalMode defaultToolsApprovalMode() {
        return defaultToolsApprovalMode;
    }
    public Map<String, McpToolConfig> tools() {
        return tools;
    }

    /** MCP Server 的互斥传输配置。 */
    public sealed interface Transport permits Stdio, StreamableHttp {}

    /** stdio 子进程传输配置。 */
    public record Stdio(
            String command,
            List<String> args,
            Map<String, String> env,
            List<McpEnvVar> envVars,
            Path cwd)
            implements Transport {
        public Stdio {
            command = requireText(command, "command");
            args = List.copyOf(args);
            args.forEach(value -> Objects.requireNonNull(value, "args element"));
            env = immutableStringMap(env, "env");
            envVars = List.copyOf(envVars);
        }
    }

    /** Streamable HTTP 传输配置。 */
    public record StreamableHttp(
            URI url,
            String bearerTokenEnvVar,
            Map<String, String> httpHeaders,
            Map<String, String> envHttpHeaders,
            String httpHeadersHelper,
            McpAuthMode auth,
            McpOAuthConfig oauth)
            implements Transport {
        public StreamableHttp {
            Objects.requireNonNull(url, "url");
            var scheme = url.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("MCP URL must use http or https");
            }
            if (bearerTokenEnvVar != null) {
                bearerTokenEnvVar = requireText(bearerTokenEnvVar, "bearerTokenEnvVar");
            }
            httpHeaders = immutableStringMap(httpHeaders, "httpHeaders");
            envHttpHeaders = immutableStringMap(envHttpHeaders, "envHttpHeaders");
            if (httpHeadersHelper != null) {
                httpHeadersHelper = requireText(httpHeadersHelper, "httpHeadersHelper");
            }
        }
    }

    /** 两种传输共享的 Builder 配置。 */
    public abstract static class Builder<B extends Builder<B>> {
        private boolean enabled = true;
        private boolean required;
        private Duration startupTimeout;
        private Duration toolTimeout;
        private boolean supportsParallelToolCalls;
        private String environmentId;
        private List<McpToolExposureSurface> omitToolsFrom;
        private List<String> enabledTools;
        private List<String> disabledTools;
        private List<String> scopes;
        private String oauthResource;
        private McpToolApprovalMode defaultToolsApprovalMode;
        private final Map<String, McpToolConfig> tools = new LinkedHashMap<>();

        protected abstract B self();

        public B enabled(boolean enabled) {
            this.enabled = enabled;
            return self();
        }

        public B required(boolean required) {
            this.required = required;
            return self();
        }

        public B startupTimeout(Duration startupTimeout) {
            this.startupTimeout = Objects.requireNonNull(startupTimeout, "startupTimeout");
            return self();
        }

        public B toolTimeout(Duration toolTimeout) {
            this.toolTimeout = Objects.requireNonNull(toolTimeout, "toolTimeout");
            return self();
        }

        public B supportsParallelToolCalls(boolean supportsParallelToolCalls) {
            this.supportsParallelToolCalls = supportsParallelToolCalls;
            return self();
        }

        public B environmentId(String environmentId) {
            this.environmentId = requireText(environmentId, "environmentId");
            return self();
        }

        public B omitToolsFrom(McpToolExposureSurface... surfaces) {
            omitToolsFrom = List.of(surfaces);
            return self();
        }

        public B enabledTools(String... enabledTools) {
            this.enabledTools = List.of(enabledTools);
            return self();
        }

        public B disabledTools(String... disabledTools) {
            this.disabledTools = List.of(disabledTools);
            return self();
        }

        public B scopes(String... scopes) {
            this.scopes = List.of(scopes);
            return self();
        }

        public B oauthResource(String oauthResource) {
            this.oauthResource = requireText(oauthResource, "oauthResource");
            return self();
        }

        public B defaultToolsApprovalMode(McpToolApprovalMode mode) {
            defaultToolsApprovalMode = Objects.requireNonNull(mode, "mode");
            return self();
        }

        public B tool(String name, McpToolConfig config) {
            tools.put(requireText(name, "tool name"), Objects.requireNonNull(config, "config"));
            return self();
        }

        public abstract McpServerConfig build();
    }

    /** stdio MCP Server Builder。 */
    public static final class StdioBuilder extends Builder<StdioBuilder> {
        private final String command;
        private final List<String> args = new ArrayList<>();
        private final Map<String, String> env = new LinkedHashMap<>();
        private final List<McpEnvVar> envVars = new ArrayList<>();
        private Path cwd;

        private StdioBuilder(String command) {
            this.command = requireText(command, "command");
        }

        @Override
        protected StdioBuilder self() {
            return this;
        }

        public StdioBuilder args(String... args) {
            this.args.addAll(List.of(args));
            return this;
        }

        public StdioBuilder env(String name, String value) {
            env.put(requireText(name, "environment variable name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public StdioBuilder envVar(McpEnvVar envVar) {
            envVars.add(Objects.requireNonNull(envVar, "envVar"));
            return this;
        }

        public StdioBuilder cwd(Path cwd) {
            this.cwd = Objects.requireNonNull(cwd, "cwd");
            return this;
        }

        @Override
        public McpServerConfig build() {
            return new McpServerConfig(this, new Stdio(command, args, env, envVars, cwd));
        }
    }

    /** Streamable HTTP MCP Server Builder。 */
    public static final class HttpBuilder extends Builder<HttpBuilder> {
        private final URI url;
        private String bearerTokenEnvVar;
        private final Map<String, String> httpHeaders = new LinkedHashMap<>();
        private final Map<String, String> envHttpHeaders = new LinkedHashMap<>();
        private String httpHeadersHelper;
        private McpAuthMode auth;
        private McpOAuthConfig oauth;

        private HttpBuilder(URI url) {
            this.url = Objects.requireNonNull(url, "url");
        }

        @Override
        protected HttpBuilder self() {
            return this;
        }

        public HttpBuilder bearerTokenEnvVar(String bearerTokenEnvVar) {
            this.bearerTokenEnvVar = requireText(bearerTokenEnvVar, "bearerTokenEnvVar");
            return this;
        }

        public HttpBuilder httpHeader(String name, String value) {
            httpHeaders.put(requireText(name, "HTTP header name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public HttpBuilder envHttpHeader(String name, String environmentVariable) {
            envHttpHeaders.put(
                    requireText(name, "HTTP header name"),
                    requireText(environmentVariable, "environment variable name"));
            return this;
        }

        public HttpBuilder httpHeadersHelper(String command) {
            httpHeadersHelper = requireText(command, "httpHeadersHelper");
            return this;
        }

        public HttpBuilder auth(McpAuthMode auth) {
            this.auth = Objects.requireNonNull(auth, "auth");
            return this;
        }

        public HttpBuilder oauth(McpOAuthConfig oauth) {
            this.oauth = Objects.requireNonNull(oauth, "oauth");
            return this;
        }

        @Override
        public McpServerConfig build() {
            return new McpServerConfig(
                    this,
                    new StreamableHttp(
                            url,
                            bearerTokenEnvVar,
                            httpHeaders,
                            envHttpHeaders,
                            httpHeadersHelper,
                            auth,
                            oauth));
        }
    }

    private static Duration nonNegative(Duration value, String name) {
        if (value != null && value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static List<String> copyOptionalNames(List<String> values, String name) {
        if (values == null) return null;
        var copy = List.copyOf(values);
        copy.forEach(value -> requireText(value, name + " element"));
        return copy;
    }

    private static <V> Map<String, V> immutableMap(Map<String, V> values) {
        var copy = new LinkedHashMap<String, V>();
        values.forEach((name, value) -> copy.put(
                requireText(name, "map key"), Objects.requireNonNull(value, "map value")));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, String> immutableStringMap(Map<String, String> values, String name) {
        var copy = new LinkedHashMap<String, String>();
        values.forEach((key, value) -> copy.put(
                requireText(key, name + " key"), Objects.requireNonNull(value, name + " value")));
        return Collections.unmodifiableMap(copy);
    }
}
