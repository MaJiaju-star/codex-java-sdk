package io.github.majiajustar.codex;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.majiajustar.codex.thread.ThreadOptions;
import io.github.majiajustar.codex.tool.ApprovalHandler;
import io.github.majiajustar.codex.tool.ApprovalRequest;
import io.github.majiajustar.codex.tool.ToolCallContext;
import io.github.majiajustar.codex.tool.ToolInterceptor;
import io.github.majiajustar.codex.tool.ToolObserver;
import io.github.majiajustar.codex.internal.JsonSupport;
import io.github.majiajustar.codex.internal.McpConfigOverrideSerializer;
import io.github.majiajustar.codex.mcp.McpServerConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Codex 客户端的进程、身份、传输、审批、观察和重试配置。
 *
 * @param command 用于启动 {@code codex app-server} 的命令及参数
 * @param workingDirectory 子进程工作目录；为 {@code null} 时继承当前目录
 * @param environment 追加到子进程的环境变量
 * @param clientName 初始化时发送的机器可读客户端名称
 * @param clientTitle 初始化时发送的可读客户端标题
 * @param clientVersion 初始化时发送的客户端版本
 * @param experimentalApi 是否启用 app-server 实验性 API
 * @param requestTimeout 每个请求和异步回调的最长等待时间
 * @param serverRequestHandler 服务器主动请求的后备处理器
 * @param approvalHandler 强类型命令及文件变更审批处理器
 * @param toolInterceptors 按注册顺序执行的工具拦截器
 * @param toolObservers 按注册顺序执行的工具生命周期观察器
 * @param retryPolicy 已识别过载响应的重试策略
 */
public record CodexClientConfig(
        List<String> command,
        Path workingDirectory,
        Map<String, String> environment,
        String clientName,
        String clientTitle,
        String clientVersion,
        boolean experimentalApi,
        Duration requestTimeout,
        BiFunction<String, JsonNode, JsonNode> serverRequestHandler,
        ApprovalHandler approvalHandler,
        List<ToolInterceptor> toolInterceptors,
        List<ToolObserver> toolObservers,
        RetryPolicy retryPolicy) {

    public CodexClientConfig {
        command = List.copyOf(command);
        environment = Map.copyOf(environment);
        Objects.requireNonNull(clientName, "clientName");
        Objects.requireNonNull(clientTitle, "clientTitle");
        Objects.requireNonNull(clientVersion, "clientVersion");
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        Objects.requireNonNull(serverRequestHandler, "serverRequestHandler");
        Objects.requireNonNull(approvalHandler, "approvalHandler");
        toolInterceptors = List.copyOf(toolInterceptors);
        toolObservers = List.copyOf(toolObservers);
        Objects.requireNonNull(retryPolicy, "retryPolicy");
    }

    /** 兼容尚未加入过载重试配置时创建的旧版配置。 */
    public CodexClientConfig(
            List<String> command,
            Path workingDirectory,
            Map<String, String> environment,
            String clientName,
            String clientTitle,
            String clientVersion,
            boolean experimentalApi,
            Duration requestTimeout,
            BiFunction<String, JsonNode, JsonNode> serverRequestHandler,
            ApprovalHandler approvalHandler,
            List<ToolInterceptor> toolInterceptors,
            List<ToolObserver> toolObservers) {
        this(
                command,
                workingDirectory,
                environment,
                clientName,
                clientTitle,
                clientVersion,
                experimentalApi,
                requestTimeout,
                serverRequestHandler,
                approvalHandler,
                toolInterceptors,
                toolObservers,
                RetryPolicy.overloadDefaults());
    }

    /** 兼容仍在使用原始服务器请求处理器的应用。 */
    public CodexClientConfig(
            List<String> command,
            Path workingDirectory,
            Map<String, String> environment,
            String clientName,
            String clientTitle,
            String clientVersion,
            boolean experimentalApi,
            Duration requestTimeout,
            BiFunction<String, JsonNode, JsonNode> serverRequestHandler) {
        this(
                command,
                workingDirectory,
                environment,
                clientName,
                clientTitle,
                clientVersion,
                experimentalApi,
                requestTimeout,
                serverRequestHandler,
                adaptLegacyApprovalHandler(serverRequestHandler),
                List.of(),
                List.of(),
                RetryPolicy.overloadDefaults());
    }

    /** 返回已设置当前平台默认值的构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    /** 用于构建不可变 {@link CodexClientConfig}。 */
    public static final class Builder {
        private List<String> command = defaultCommand();
        private Path workingDirectory;
        private final Map<String, String> environment = new LinkedHashMap<>();
        private final List<String> configOverrides = new ArrayList<>();
        private final Map<String, McpServerConfig> mcpServers = new LinkedHashMap<>();
        private String baseUrl;
        private String model;
        private String modelProvider;
        private ReasoningEffort modelReasoningEffort;
        private WebSearchMode webSearch;
        private ThreadOptions.ApprovalPolicy approvalPolicy;
        private Boolean workspaceNetworkAccess;
        private String clientName = "codex_java_sdk";
        private String clientTitle = "Codex Java SDK";
        private String clientVersion = "0.0.1-SNAPSHOT";
        private boolean experimentalApi = true;
        private Duration requestTimeout = Duration.ofSeconds(60);
        private BiFunction<String, JsonNode, JsonNode> serverRequestHandler =
                CodexClientConfig::acceptStandardApproval;
        private ApprovalHandler approvalHandler = ApprovalHandler.acceptAll();
        private final List<ToolInterceptor> toolInterceptors = new ArrayList<>();
        private final List<ToolObserver> toolObservers = new ArrayList<>();
        private RetryPolicy retryPolicy = RetryPolicy.overloadDefaults();

        /** 替换完整的子进程命令及其参数。 */
        public Builder command(List<String> command) {
            this.command = new ArrayList<>(command);
            return this;
        }

        /** 指定 Codex 可执行文件并自动追加 {@code app-server} 参数。 */
        public Builder codexExecutable(Path executable) {
            this.command = List.of(executable.toString(), "app-server");
            return this;
        }

        /** 设置 app-server 子进程的工作目录。 */
        public Builder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        /** 新增或替换一个子进程环境变量。 */
        public Builder environment(String name, String value) {
            environment.put(name, value);
            return this;
        }

        /** Configure an OpenAI-compatible API base URL for the app-server process. */
        public Builder baseUrl(String value) {
            baseUrl = Objects.requireNonNull(value, "value");
            return this;
        }

        /** Configure the default model used by new threads. */
        public Builder model(String value) {
            model = Objects.requireNonNull(value, "value");
            return this;
        }

        /** Configure the default model-provider id. */
        public Builder modelProvider(String value) {
            modelProvider = Objects.requireNonNull(value, "value");
            return this;
        }

        /** Configure the default model reasoning effort. */
        public Builder modelReasoningEffort(ReasoningEffort value) {
            modelReasoningEffort = Objects.requireNonNull(value, "value");
            return this;
        }

        /** Configure whether web search is disabled, cached, or live. */
        public Builder webSearch(WebSearchMode value) {
            webSearch = Objects.requireNonNull(value, "value");
            return this;
        }

        /** Configure the default approval policy for new threads. */
        public Builder approvalPolicy(ThreadOptions.ApprovalPolicy value) {
            approvalPolicy = Objects.requireNonNull(value, "value");
            return this;
        }

        /** Configure network access for the default workspace-write sandbox. */
        public Builder workspaceNetworkAccess(boolean value) {
            workspaceNetworkAccess = value;
            return this;
        }

        /** Configure API-key authentication without requiring an interactive login. */
        public Builder apiKey(String value) {
            environment.put("OPENAI_API_KEY", Objects.requireNonNull(value, "value"));
            return this;
        }

        /** Append one raw Codex CLI {@code --config key=value} override. */
        public Builder configOverride(String value) {
            configOverrides.add(Objects.requireNonNull(value, "value"));
            return this;
        }

        /** Append raw Codex CLI {@code --config key=value} overrides in precedence order. */
        public Builder configOverrides(List<String> values) {
            values.forEach(this::configOverride);
            return this;
        }

        /** 注册一个在 app-server 启动时初始化的 MCP Server；同名配置以后一次为准。 */
        public Builder mcpServer(String name, McpServerConfig config) {
            McpConfigOverrideSerializer.validateName(name);
            mcpServers.put(name, Objects.requireNonNull(config, "config"));
            return this;
        }

        /** 批量注册在 app-server 启动时初始化的 MCP Server。 */
        public Builder mcpServers(Map<String, McpServerConfig> configs) {
            Objects.requireNonNull(configs, "configs").forEach(this::mcpServer);
            return this;
        }

        /** 设置初始化请求中发送的客户端身份信息。 */
        public Builder clientInfo(String name, String title, String version) {
            clientName = name;
            clientTitle = title;
            clientVersion = version;
            return this;
        }

        /** 启用或禁用 app-server 实验性 API 能力。 */
        public Builder experimentalApi(boolean experimentalApi) {
            this.experimentalApi = experimentalApi;
            return this;
        }

        /** 设置 JSON-RPC 请求和审批回调的超时时间。 */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * 设置原始服务器请求的后备处理器。
         *
         * <p>为了兼容旧接口，该方法还会将处理器适配为命令和文件审批处理器。新应用应优先使用
         * {@link #approvalHandler(ApprovalHandler)}。
         */
        public Builder serverRequestHandler(BiFunction<String, JsonNode, JsonNode> handler) {
            serverRequestHandler = handler;
            approvalHandler = adaptLegacyApprovalHandler(handler);
            return this;
        }

        /** 设置强类型命令及文件变更审批处理器。 */
        public Builder approvalHandler(ApprovalHandler handler) {
            approvalHandler = Objects.requireNonNull(handler, "handler");
            return this;
        }

        /** 向有序工具拦截器链追加一个拦截器。 */
        public Builder toolInterceptor(ToolInterceptor interceptor) {
            toolInterceptors.add(Objects.requireNonNull(interceptor, "interceptor"));
            return this;
        }

        /** 追加一个不参与控制决策的工具生命周期观察器。 */
        public Builder toolObserver(ToolObserver observer) {
            toolObservers.add(Objects.requireNonNull(observer, "observer"));
            return this;
        }

        /** 设置仅用于已识别过载响应的重试策略。 */
        public Builder retryPolicy(RetryPolicy value) {
            retryPolicy = Objects.requireNonNull(value, "value");
            return this;
        }

        /** 创建不可变客户端配置。 */
        public CodexClientConfig build() {
            var resolvedOverrides = new ArrayList<>(configOverrides);
            if (baseUrl != null) {
                resolvedOverrides.add("openai_base_url=" + tomlString(baseUrl));
            }
            if (modelProvider != null) {
                resolvedOverrides.add("model_provider=" + tomlString(modelProvider));
            }
            if (model != null) resolvedOverrides.add("model=" + tomlString(model));
            if (modelReasoningEffort != null) {
                resolvedOverrides.add(
                        "model_reasoning_effort=" + tomlString(modelReasoningEffort.wireValue()));
            }
            if (webSearch != null) {
                resolvedOverrides.add("web_search=" + tomlString(webSearch.wireValue()));
            }
            if (approvalPolicy != null) {
                resolvedOverrides.add("approval_policy=" + tomlString(approvalPolicy.wireValue()));
            }
            if (workspaceNetworkAccess != null) {
                resolvedOverrides.add(
                        "sandbox_workspace_write.network_access=" + workspaceNetworkAccess);
            }
            mcpServers.forEach((name, config) ->
                    resolvedOverrides.add(McpConfigOverrideSerializer.serialize(name, config)));
            return new CodexClientConfig(
                    withConfigOverrides(command, resolvedOverrides),
                    workingDirectory,
                    environment,
                    clientName,
                    clientTitle,
                    clientVersion,
                    experimentalApi,
                    requestTimeout,
                    serverRequestHandler,
                    approvalHandler,
                    toolInterceptors,
                    toolObservers,
                    retryPolicy);
        }

        private static List<String> withConfigOverrides(
                List<String> command, List<String> overrides) {
            if (overrides.isEmpty()) return List.copyOf(command);
            var appServerIndex = command.lastIndexOf("app-server");
            if (appServerIndex < 0) {
                throw new IllegalStateException(
                        "config overrides require a Codex CLI command containing 'app-server'");
            }
            var resolved = new ArrayList<String>(command.size() + overrides.size() * 2);
            resolved.addAll(command.subList(0, appServerIndex));
            overrides.forEach(override -> {
                resolved.add("--config");
                resolved.add(override);
            });
            resolved.addAll(command.subList(appServerIndex, command.size()));
            return List.copyOf(resolved);
        }

        private static String tomlString(String value) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }

        private static List<String> defaultCommand() {
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                return List.of("cmd.exe", "/d", "/c", "codex", "app-server");
            }
            return List.of("codex", "app-server");
        }
    }

    /** Web search mode accepted by the Codex {@code web_search} configuration key. */
    public enum WebSearchMode {
        DISABLED("disabled"),
        CACHED("cached"),
        LIVE("live");

        private final String wireValue;

        WebSearchMode(String wireValue) {
            this.wireValue = wireValue;
        }

        /** Return the exact Codex configuration value. */
        public String wireValue() {
            return wireValue;
        }
    }

    /** Reasoning effort accepted by the Codex {@code model_reasoning_effort} key. */
    public enum ReasoningEffort {
        MINIMAL("minimal"),
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high"),
        XHIGH("xhigh"),
        MAX("max"),
        ULTRA("ultra");

        private final String wireValue;

        ReasoningEffort(String wireValue) {
            this.wireValue = wireValue;
        }

        /** Return the exact Codex configuration value. */
        public String wireValue() {
            return wireValue;
        }
    }

    private static JsonNode acceptStandardApproval(String method, JsonNode ignored) {
        var response = JsonSupport.MAPPER.createObjectNode();
        if (method.equals("item/commandExecution/requestApproval")
                || method.equals("item/fileChange/requestApproval")) {
            response.put("decision", "accept");
        }
        return response;
    }

    private static ApprovalHandler adaptLegacyApprovalHandler(BiFunction<String, JsonNode, JsonNode> handler) {
        Objects.requireNonNull(handler, "handler");
        return request -> {
            var method = request.context().kind() == ToolCallContext.Kind.COMMAND
                    ? "item/commandExecution/requestApproval"
                    : "item/fileChange/requestApproval";
            var result = handler.apply(method, request.raw());
            var decision = result == null
                    ? ApprovalRequest.Decision.DECLINE
                    : ApprovalRequest.Decision.fromWireValue(result.path("decision").asText());
            return CompletableFuture.completedFuture(decision);
        };
    }
}
