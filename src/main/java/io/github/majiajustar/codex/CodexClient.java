package io.github.majiajustar.codex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.majiajustar.codex.event.CodexEvent;
import io.github.majiajustar.codex.event.CodexEventType;
import io.github.majiajustar.codex.config.ConfigClient;
import io.github.majiajustar.codex.exception.CodexException;
import io.github.majiajustar.codex.exception.CodexTimeoutException;
import io.github.majiajustar.codex.exception.CodexTransportException;
import io.github.majiajustar.codex.exception.InternalRpcException;
import io.github.majiajustar.codex.exception.InvalidParamsException;
import io.github.majiajustar.codex.exception.InvalidRequestException;
import io.github.majiajustar.codex.exception.JsonRpcException;
import io.github.majiajustar.codex.exception.JsonRpcParseException;
import io.github.majiajustar.codex.exception.MethodNotFoundException;
import io.github.majiajustar.codex.exception.RetryLimitExceededException;
import io.github.majiajustar.codex.exception.ServerBusyException;
import io.github.majiajustar.codex.generated.v2.ThreadArchiveParams;
import io.github.majiajustar.codex.generated.v2.ThreadArchiveResponse;
import io.github.majiajustar.codex.generated.v2.ThreadListResponse;
import io.github.majiajustar.codex.generated.v2.ThreadUnarchiveParams;
import io.github.majiajustar.codex.generated.v2.ThreadUnarchiveResponse;
import io.github.majiajustar.codex.internal.JsonSupport;
import io.github.majiajustar.codex.internal.ToolLifecycleDispatcher;
import io.github.majiajustar.codex.model.CodexModelList;
import io.github.majiajustar.codex.mcp.McpClient;
import io.github.majiajustar.codex.skills.SkillsClient;
import io.github.majiajustar.codex.thread.ThreadListOptions;
import io.github.majiajustar.codex.thread.ThreadOptions;
import io.github.majiajustar.codex.turn.TurnOptions;
import io.github.majiajustar.codex.turn.UserInput;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 控制本地 {@code codex app-server} 进程的入口。
 *
 * <p>客户端拥有子进程、虚拟线程执行器以及所有活动流。每个 app-server 进程应创建一个客户端，
 * 并在应用关闭时释放它。公开请求方法支持并发调用。
 */
public final class CodexClient implements AutoCloseable {
    private static final ObjectMapper MAPPER = JsonSupport.MAPPER;
    private static final int STDERR_LIMIT = 400;
    private static final int EARLY_EVENT_LIMIT = 4096;

    private final CodexClientConfig config;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ToolLifecycleDispatcher toolLifecycle;
    private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final Map<String, CodexTurn.EventChannel> turns = new ConcurrentHashMap<>();
    private final Map<String, ArrayDeque<CodexEvent>> earlyTurnEvents = new ConcurrentHashMap<>();
    private final SubmissionPublisher<CodexEvent> events = new SubmissionPublisher<>(executor, 256);
    private final ArrayDeque<String> stderr = new ArrayDeque<>();
    private final Object writeLock = new Object();
    private final Object routeLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private Process process;
    private BufferedWriter writer;
    private JsonNode metadata;
    private final McpClient mcp;
    private final SkillsClient skills;
    private final ConfigClient configApi;

    private CodexClient(CodexClientConfig config) {
        this.config = config;
        toolLifecycle = new ToolLifecycleDispatcher(config);
        mcp = new McpClient(this);
        skills = new SkillsClient(this);
        configApi = new ConfigClient(this);
    }

    /**
     * 使用 {@link CodexClientConfig.Builder} 的默认配置启动客户端。
     *
     * @return 已完成初始化的客户端
     * @throws CodexException 进程无法启动或初始化失败时抛出
     */
    public static CodexClient create() {
        return create(CodexClientConfig.builder().build());
    }

    /**
     * 启动并初始化客户端。
     *
     * @param config 进程及传输配置
     * @return 已完成初始化的客户端
     * @throws CodexException 进程无法启动或初始化失败时抛出
     */
    public static CodexClient create(CodexClientConfig config) {
        var client = new CodexClient(config);
        try {
            client.start();
            client.initialize();
            return client;
        } catch (RuntimeException error) {
            client.close();
            throw error;
        }
    }

    /** 返回 app-server {@code initialize} 请求的原始结果。 */
    public JsonNode metadata() {
        return metadata;
    }

    /**
     * 发布订阅建立后收到的全部通知。
     *
     * <p>该客户端级发布器不会重放较早的事件。若新轮次需要保留订阅前已经到达的事件，
     * 请使用 {@link CodexTurn#events()}。
     */
    public java.util.concurrent.Flow.Publisher<CodexEvent> events() {
        return events;
    }

    /** 返回与当前 app-server 连接关联的 MCP 运行时客户端。 */
    public McpClient mcp() {
        return mcp;
    }

    /** 返回与当前 app-server 连接关联的 Skills 客户端。 */
    public SkillsClient skills() {
        return skills;
    }

    /** 返回分层配置读写和管理员约束 API。 */
    public ConfigClient config() {
        return configApi;
    }

    /** 使用默认选项启动一个持久化会话。 */
    public CodexThread startThread() {
        return startThread(ThreadOptions.defaults());
    }

    /** 使用指定选项启动一个持久化会话。 */
    public CodexThread startThread(ThreadOptions options) {
        var result = request("thread/start", options.toStartJson());
        return new CodexThread(this, requiredText(result, "thread", "id"));
    }

    /** 使用默认覆盖配置恢复已有会话。 */
    public CodexThread resumeThread(String threadId) {
        return resumeThread(threadId, ThreadOptions.defaults());
    }

    /**
     * 恢复已有会话。
     *
     * @param threadId app-server 会话 ID
     * @param options 恢复会话时使用的配置覆盖项
     */
    public CodexThread resumeThread(String threadId, ThreadOptions options) {
        var params = options.toResumeJson().put("threadId", threadId);
        var result = request("thread/resume", params);
        return new CodexThread(this, requiredText(result, "thread", "id"));
    }

    /** 使用默认选项派生已有会话。 */
    public CodexThread forkThread(String threadId) {
        return forkThread(threadId, ThreadOptions.defaults());
    }

    /**
     * 从已有会话派生出一个新对话。
     *
     * @param threadId 源会话 ID
     * @param options 派生配置，其中可包含最后一个轮次 ID
     */
    public CodexThread forkThread(String threadId, ThreadOptions options) {
        var params = options.toForkJson().put("threadId", threadId);
        var result = request("thread/fork", params);
        return new CodexThread(this, requiredText(result, "thread", "id"));
    }

    /**
     * 通过原始协议 API 列出会话。
     *
     * <p>常规场景优先使用 {@link #listThreads(ThreadListOptions)}。保留该重载是为了访问新版
     * app-server 新增而当前强类型尚未覆盖的字段。
     */
    public JsonNode listThreads(JsonNode params) {
        return request("thread/list", params);
    }

    /** 返回一页符合过滤条件的强类型会话数据。 */
    public ThreadListResponse listThreads(ThreadListOptions options) {
        var result = request("thread/list", MAPPER.valueToTree(options.toParams()));
        return decode(result, ThreadListResponse.class);
    }

    /** 使用服务器默认设置返回第一页活动会话。 */
    public ThreadListResponse listThreads() {
        return listThreads(ThreadListOptions.defaults());
    }

    /** 归档指定会话并返回强类型协议响应。 */
    public ThreadArchiveResponse archiveThread(String threadId) {
        var params = MAPPER.valueToTree(new ThreadArchiveParams(threadId));
        return decode(request("thread/archive", params), ThreadArchiveResponse.class);
    }

    /** 恢复指定的已归档会话并返回其当前表示。 */
    public ThreadUnarchiveResponse unarchiveThread(String threadId) {
        var params = MAPPER.valueToTree(new ThreadUnarchiveParams(threadId));
        return decode(request("thread/unarchive", params), ThreadUnarchiveResponse.class);
    }

    /**
     * 返回原始模型目录。
     *
     * @param includeHidden 是否包含隐藏模型
     */
    public JsonNode models(boolean includeHidden) {
        return request("model/list", MAPPER.createObjectNode().put("includeHidden", includeHidden));
    }

    /** Return the first page of the strongly typed model catalog. */
    public CodexModelList listModels(boolean includeHidden) {
        return CodexModelList.from(models(includeHidden));
    }

    /** Return the default visible page of the strongly typed model catalog. */
    public CodexModelList listModels() {
        return listModels(false);
    }

    /**
     * 执行同步 JSON-RPC 请求。
     *
     * <p>只有被识别为过载的响应才会根据 {@link CodexClientConfig#retryPolicy()} 自动重试，
     * 其他错误会立即失败。
     *
     * @param method JSON-RPC 方法名
     * @param params 请求参数；可以为 {@code null}
     * @return 原始结果载荷
     * @throws JsonRpcException app-server 返回错误时抛出
     * @throws CodexTimeoutException 超过配置的请求超时时间时抛出
     * @throws CodexTransportException app-server 传输失败时抛出
     */
    public JsonNode request(String method, JsonNode params) {
        var policy = config.retryPolicy();
        for (var attempt = 1; ; attempt++) {
            try {
                return await(requestOnceAsync(method, params));
            } catch (ServerBusyException error) {
                if (attempt >= policy.maxAttempts()) throw error;
                sleepBeforeRetry(policy.delayAfterAttempt(attempt));
            }
        }
    }

    /**
     * 在客户端的虚拟线程执行器上执行 JSON-RPC 请求。
     *
     * @param method JSON-RPC 方法名
     * @param params 请求参数；可以为 {@code null}
     * @return 包含原始结果载荷的 future
     */
    public CompletableFuture<JsonNode> requestAsync(String method, JsonNode params) {
        ensureOpen();
        return CompletableFuture.supplyAsync(() -> request(method, params), executor);
    }

    private CompletableFuture<JsonNode> requestOnceAsync(String method, JsonNode params) {
        ensureOpen();
        var id = UUID.randomUUID().toString();
        var response = new CompletableFuture<JsonNode>();
        pending.put(id, response);
        var message = MAPPER.createObjectNode().put("id", id).put("method", method);
        if (params != null) message.set("params", params);
        try {
            write(message);
        } catch (RuntimeException error) {
            pending.remove(id);
            response.completeExceptionally(error);
        }
        return response;
    }

    void notify(String method, JsonNode params) {
        var message = MAPPER.createObjectNode().put("method", method);
        if (params != null) message.set("params", params);
        write(message);
    }

    CodexTurn startTurn(String threadId, List<UserInput> input, TurnOptions options) {
        var params = options.toJson().put("threadId", threadId);
        var inputJson = params.putArray("input");
        input.forEach(item -> inputJson.add(item.toJson(MAPPER)));
        var result = request("turn/start", params);
        var turnId = requiredText(result, "turn", "id");
        var channel = registerTurn(turnId);
        return new CodexTurn(this, threadId, turnId, channel, executor);
    }

    private void start() {
        try {
            var builder = new ProcessBuilder(config.command());
            if (config.workingDirectory() != null) builder.directory(config.workingDirectory().toFile());
            builder.environment().putAll(config.environment());
            process = builder.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            executor.submit(() -> readStdout(process));
            executor.submit(() -> readStderr(process));
        } catch (IOException error) {
            throw new CodexTransportException("Unable to start Codex command: " + config.command(), error);
        }
    }

    private void initialize() {
        var params = MAPPER.createObjectNode();
        params.putObject("clientInfo")
                .put("name", config.clientName())
                .put("title", config.clientTitle())
                .put("version", config.clientVersion());
        params.putObject("capabilities").put("experimentalApi", config.experimentalApi());
        metadata = request("initialize", params);
        notify("initialized", null);
    }

    private void readStdout(Process running) {
        try (var reader = new BufferedReader(
                new InputStreamReader(running.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) route(MAPPER.readTree(line));
            if (!closed.get()) failTransport("Codex app-server closed stdout");
        } catch (Exception error) {
            if (!closed.get()) failTransport("Failed to read Codex app-server output", error);
        }
    }

    private void readStderr(Process running) {
        try (var reader = new BufferedReader(
                new InputStreamReader(running.getErrorStream(), StandardCharsets.UTF_8))) {
            reader.lines().forEach(line -> {
                synchronized (stderr) {
                    if (stderr.size() == STDERR_LIMIT) stderr.removeFirst();
                    stderr.addLast(line);
                }
            });
        } catch (IOException ignored) {
            // 进程关闭时会同时关闭标准错误流。
        }
    }

    private void route(JsonNode message) {
        if (message.has("method") && message.has("id")) {
            executor.submit(() -> handleServerRequest(message));
        } else if (message.has("method")) {
            routeEvent(new CodexEvent(message.path("method").asText(), message.path("params")));
        } else if (message.has("id")) {
            var future = pending.remove(message.path("id").asText());
            if (future == null) return;
            if (message.has("error")) {
                var error = message.path("error");
                future.completeExceptionally(JsonRpcException.map(
                        error.path("code").asInt(-32000),
                        error.path("message").asText("Unknown error"),
                        error.get("data")));
            } else {
                future.complete(message.get("result"));
            }
        }
    }

    private void handleServerRequest(JsonNode message) {
        var reply = MAPPER.createObjectNode();
        reply.set("id", message.get("id"));
        try {
            var method = message.path("method").asText();
            var params = message.path("params");
            var result = toolLifecycle.handlesApproval(method)
                    ? toolLifecycle.handleApproval(method, params)
                    : config.serverRequestHandler().apply(method, params);
            reply.set("result", result == null ? MAPPER.createObjectNode() : result);
        } catch (RuntimeException error) {
            reply.putObject("error").put("code", -32603).put("message", error.getMessage());
        }
        write(reply);
    }

    private void routeEvent(CodexEvent event) {
        events.offer(event, (subscriber, dropped) -> false);
        toolLifecycle.dispatch(event);
        var turnId = turnId(event.params());
        if (turnId == null) return;
        synchronized (routeLock) {
            var channel = turns.get(turnId);
            if (channel != null) {
                channel.offer(event);
                if (event.type() == CodexEventType.TURN_COMPLETED) turns.remove(turnId);
                return;
            }
            var early = earlyTurnEvents.computeIfAbsent(turnId, ignored -> new ArrayDeque<>());
            if (early.size() == EARLY_EVENT_LIMIT) early.removeFirst();
            early.addLast(event);
        }
    }

    private CodexTurn.EventChannel registerTurn(String turnId) {
        synchronized (routeLock) {
            var channel = new CodexTurn.EventChannel();
            var early = earlyTurnEvents.remove(turnId);
            if (early != null) early.forEach(channel::offer);
            var alreadyCompleted = early != null
                    && early.stream().anyMatch(event -> event.type() == CodexEventType.TURN_COMPLETED);
            if (!alreadyCompleted) turns.put(turnId, channel);
            return channel;
        }
    }

    private static String turnId(JsonNode params) {
        var direct = params.path("turnId");
        if (direct.isTextual()) return direct.asText();
        var nested = params.path("turn").path("id");
        return nested.isTextual() ? nested.asText() : null;
    }

    private void write(ObjectNode message) {
        ensureOpen();
        synchronized (writeLock) {
            try {
                writer.write(MAPPER.writeValueAsString(message));
                writer.newLine();
                writer.flush();
            } catch (IOException error) {
                throw new CodexTransportException("Failed to write to Codex app-server", error);
            }
        }
    }

    private JsonNode await(CompletableFuture<JsonNode> future) {
        try {
            return future.get(config.requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CodexException("Interrupted while waiting for Codex app-server", error);
        } catch (ExecutionException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new CodexException("Codex request failed", error.getCause());
        } catch (TimeoutException error) {
            throw new CodexTimeoutException("Timed out waiting for Codex app-server", error);
        }
    }

    private void failTransport(String message) {
        failTransport(message, null);
    }

    private void failTransport(String message, Throwable cause) {
        var tail = stderrTail();
        var error = new CodexTransportException(
                message + (tail.isEmpty() ? "" : "; stderr: " + tail),
                cause);
        pending.values().forEach(future -> future.completeExceptionally(error));
        pending.clear();
        turns.values().forEach(channel -> channel.fail(error));
        turns.clear();
    }

    private String stderrTail() {
        synchronized (stderr) {
            var text = String.join("\n", stderr);
            return text.substring(0, Math.min(2000, text.length()));
        }
    }

    private static String requiredText(JsonNode node, String parent, String field) {
        var value = node.path(parent).path(field);
        if (!value.isTextual()) throw new CodexException("Response is missing " + parent + "." + field);
        return value.asText();
    }

    private static <T> T decode(JsonNode value, Class<T> type) {
        try {
            return MAPPER.treeToValue(value, type);
        } catch (IOException error) {
            throw new CodexTransportException(
                    "Unable to decode app-server response as " + type.getSimpleName(),
                    error);
        }
    }

    private static void sleepBeforeRetry(java.time.Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CodexException("Interrupted while waiting to retry an overloaded request", error);
        }
    }

    private void ensureOpen() {
        if (closed.get()) throw new CodexException("Codex client is closed");
    }

    /**
     * 停止接收请求、终止 app-server 子进程，并让活动操作以异常结束。
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        var closeError = new CodexException("Codex client is closed");
        pending.values().forEach(future -> future.completeExceptionally(closeError));
        pending.clear();
        turns.values().forEach(channel -> channel.fail(closeError));
        turns.clear();
        events.close();
        if (writer != null) {
            try { writer.close(); } catch (IOException ignored) { }
        }
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        toolLifecycle.close();
        executor.shutdownNow();
    }
}
