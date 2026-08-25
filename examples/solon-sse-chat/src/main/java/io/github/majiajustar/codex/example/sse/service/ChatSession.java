package io.github.majiajustar.codex.example.sse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.majiajustar.codex.CodexThread;
import io.github.majiajustar.codex.CodexTurn;
import io.github.majiajustar.codex.event.CodexEvent;
import io.github.majiajustar.codex.event.CodexEventType;
import io.github.majiajustar.codex.sandbox.SandboxPolicy;
import io.github.majiajustar.codex.tool.ApprovalRequest;
import io.github.majiajustar.codex.tool.ToolCallContext;
import io.github.majiajustar.codex.tool.ToolCallResult;
import io.github.majiajustar.codex.turn.TurnOptions;
import io.github.majiajustar.codex.turn.TurnResult;
import io.github.majiajustar.codex.turn.UserInput;
import io.github.majiajustar.codex.example.sse.model.ApiModels.BashObservation;
import io.github.majiajustar.codex.example.sse.model.ApiModels.HistoryView;
import io.github.majiajustar.codex.example.sse.model.ApiModels.SendMessageRequest;
import io.github.majiajustar.codex.example.sse.model.ApiModels.SessionView;
import io.github.majiajustar.codex.example.sse.model.ApiModels.TurnAccepted;
import io.github.majiajustar.codex.generated.v2.ReasoningSummary;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import reactor.core.publisher.Flux;

/** Owns one Codex thread, its active turn, SSE history, and workspace watcher. */
final class ChatSession implements AutoCloseable {
    private static final int TOOL_OUTPUT_CHUNK_LIMIT = 16_384;
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(api[_-]?key|access[_-]?token|auth[_-]?token|password|secret)(\\s*[=:]\\s*)([^\\s;&|]+)");

    private final String id;
    private final String name;
    private final CodexThread thread;
    private final Path workspace;
    private final SseChannel channel;
    private final WorkspaceWatcher watcher;
    private final AtomicReference<CodexTurn> activeTurn = new AtomicReference<>();
    private final ConcurrentHashMap<String, CompletableFuture<ApprovalRequest.Decision>> pendingApprovals =
            new ConcurrentHashMap<>();
    private final Instant createdAt = Instant.now();
    private volatile Instant updatedAt = createdAt;

    ChatSession(String id, String name, CodexThread thread, Path workspace, ObjectMapper mapper) throws IOException {
        this.id = id;
        this.name = name;
        this.thread = thread;
        this.workspace = workspace;
        channel = new SseChannel(mapper, id);
        watcher = new WorkspaceWatcher(workspace, (kind, path) -> {
            updatedAt = Instant.now();
            channel.publish("workspace.changed", Map.of("kind", kind, "path", path));
        });
        channel.publish("session.ready", view());
    }

    Flux<String> events() {
        return channel.flux();
    }

    HistoryView history() {
        return ThreadHistoryMapper.map(id, thread.id(), thread.read(true));
    }

    synchronized TurnAccepted send(SendMessageRequest request) {
        String message = request.message();
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message 不能为空");
        if (activeTurn.get() != null) throw new IllegalStateException("当前会话已有正在执行的 Turn");

        updatedAt = Instant.now();
        CodexTurn turn;
        try {
            TurnOptions.Builder options = TurnOptions.builder()
                    .clientUserMessageId("web-" + UUID.randomUUID())
                    .sandboxPolicy(SandboxPolicy.workspaceWrite()
                            .writableRoot(workspace)
                            .build());
            if (request.reasoningEffort() != null && !request.reasoningEffort().isBlank()) {
                options.reasoningEffort(request.reasoningEffort().strip());
            }
            if (request.reasoningSummary() != null && !request.reasoningSummary().isBlank()) {
                options.reasoningSummary(
                        ReasoningSummary.fromWireValue(request.reasoningSummary().strip()));
            }
            turn = thread.startTurn(
                    List.of(UserInput.text(message)),
                    options.build());
        } catch (RuntimeException error) {
            channel.publish("turn.error", Map.of("message", messageOf(error)));
            throw error;
        }
        activeTurn.set(turn);
        channel.publish("user.message", Map.of("turnId", turn.id(), "text", message));
        channel.publish("turn.accepted", Map.of("turnId", turn.id()));

        turn.events().subscribe(new TurnEventSubscriber(turn));
        turn.resultAsync().whenComplete((result, error) -> complete(turn, result, error));
        return new TurnAccepted(id, turn.id());
    }

    void interrupt() {
        CodexTurn turn = activeTurn.get();
        if (turn == null) throw new IllegalStateException("当前会话没有正在执行的 Turn");
        channel.publish("turn.interrupting", Map.of("turnId", turn.id()));
        turn.interrupt();
    }

    String threadId() {
        return thread.id();
    }

    String id() {
        return id;
    }

    boolean running() {
        return activeTurn.get() != null;
    }

    CompletableFuture<ApprovalRequest.Decision> requestApproval(ApprovalRequest request) {
        String approvalId = UUID.randomUUID().toString();
        CompletableFuture<ApprovalRequest.Decision> decision = new CompletableFuture<>();
        pendingApprovals.put(approvalId, decision);

        ToolCallContext context = request.context();
        LinkedHashMap<String, Object> data = toolData(context);
        data.put("approvalId", approvalId);
        data.put("reason", redact(request.reason()));
        if (request instanceof ApprovalRequest.FileChange fileChange) {
            data.put("grantRoot", fileChange.grantRoot());
        }
        channel.publish("approval.requested", data);

        decision.completeOnTimeout(ApprovalRequest.Decision.CANCEL, 9, TimeUnit.MINUTES)
                .whenComplete((resolved, error) -> {
                    pendingApprovals.remove(approvalId, decision);
                    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
                    result.put("approvalId", approvalId);
                    result.put("itemId", context.itemId());
                    result.put("decision", error == null ? resolved.wireValue() : "cancel");
                    if (error != null) result.put("error", messageOf(error));
                    channel.publish("approval.resolved", result);
                });
        return decision;
    }

    void resolveApproval(String approvalId, ApprovalRequest.Decision decision) {
        if (approvalId == null || approvalId.isBlank()) {
            throw new IllegalArgumentException("approvalId 不能为空");
        }
        CompletableFuture<ApprovalRequest.Decision> pending = pendingApprovals.get(approvalId);
        if (pending == null || !pending.complete(decision)) {
            throw new IllegalArgumentException("审批不存在、已处理或已超时: " + approvalId);
        }
    }

    void toolStarted(ToolCallContext context) {
        LinkedHashMap<String, Object> data = toolData(context);
        if (context.kind() == ToolCallContext.Kind.COMMAND) {
            BashObservation observation = BashCommandMonitor.inspect(context.raw(), workspace);
            data.put("command", redact(observation.command()));
            data.put("observation", new BashObservation(
                    observation.operation(), redact(observation.command()), observation.paths()));
        }
        channel.publish(toolEventName(context.kind(), "started"), data);
    }

    void toolOutput(ToolCallContext context, String delta) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", context.itemId());
        data.put("turnId", context.turnId());
        data.put("delta", redact(limitOutput(delta)));
        channel.publish(toolEventName(context.kind(), "output"), data);
    }

    void toolCompleted(ToolCallResult result) {
        LinkedHashMap<String, Object> data = toolData(result.context());
        data.put("successful", result.successful());
        data.put("error", redact(result.error()));
        JsonNode status = result.item().path("status");
        if (status.isTextual()) data.put("status", status.asText());
        channel.publish(toolEventName(result.context().kind(), "completed"), data);
    }

    SessionView view() {
        CodexTurn turn = activeTurn.get();
        return new SessionView(
                id,
                name,
                thread.id(),
                workspace.toString(),
                turn != null,
                turn == null ? null : turn.id(),
                createdAt.toString(),
                updatedAt.toString());
    }

    private void complete(CodexTurn turn, TurnResult result, Throwable error) {
        activeTurn.compareAndSet(turn, null);
        updatedAt = Instant.now();
        if (error != null) {
            channel.publish("turn.error", Map.of("turnId", turn.id(), "message", messageOf(error)));
            return;
        }
        if (result.finalResponse() != null && !result.finalResponse().isBlank()) {
            channel.publish("assistant.final", Map.of("turnId", turn.id(), "text", result.finalResponse()));
        }
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("turnId", turn.id());
        data.put("status", result.status());
        data.put("usage", result.usage());
        channel.publish("turn.completed", data);
    }

    private void route(CodexEvent event) {
        updatedAt = Instant.now();
        JsonNode params = event.params();
        switch (event.type()) {
            case TURN_STARTED -> channel.publish("turn.started", params);
            case TURN_COMPLETED -> channel.publish("turn.protocolCompleted", params);
            case AGENT_MESSAGE_DELTA -> channel.publish(
                    "assistant.delta",
                    Map.of(
                            "turnId", params.path("turnId").asText(""),
                            "delta", params.path("delta").asText("")));
            case REASONING_TEXT_DELTA, REASONING_SUMMARY_TEXT_DELTA ->
                    channel.publish("assistant.reasoning", params);
            case COMMAND_OUTPUT_DELTA -> {
                // ToolObserver emits the correlated and bounded command output event.
            }
            case TOKEN_USAGE_UPDATED -> channel.publish("usage.updated", params);
            case ITEM_STARTED -> routeNonToolItem("started", params.path("item"));
            case ITEM_COMPLETED -> routeNonToolItem("completed", params.path("item"));
            case FILE_CHANGE_PATCH_UPDATED -> channel.publish("tool.fileChange.patch", params);
            case MCP_TOOL_CALL_PROGRESS -> channel.publish("tool.mcp.progress", params);
            case PLAN_DELTA -> channel.publish("assistant.plan", params);
            case THREAD_STARTED, THREAD_STATUS_CHANGED -> channel.publish("thread.updated", params);
            case ERROR -> channel.publish("turn.error", params);
            case UNKNOWN -> channel.publish(
                    "codex.unknown", Map.of("method", event.method(), "params", params));
        }
    }

    private void routeNonToolItem(String phase, JsonNode item) {
        String type = item.path("type").asText("unknown");
        switch (type) {
            case "commandExecution", "mcpToolCall", "fileChange", "webSearch", "agentMessage", "reasoning", "plan" -> {
                // Tools are emitted by ToolObserver; the other items have dedicated handling.
            }
            default -> channel.publish("item." + phase, item);
        }
    }

    private LinkedHashMap<String, Object> toolData(ToolCallContext context) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("threadId", context.threadId());
        data.put("turnId", context.turnId());
        data.put("itemId", context.itemId());
        data.put("kind", context.kind().name().toLowerCase());
        data.put("toolName", context.toolName());
        data.put("command", redact(context.command()));
        data.put("workingDirectory", context.workingDirectory());
        return data;
    }

    private static String toolEventName(ToolCallContext.Kind kind, String phase) {
        String tool = switch (kind) {
            case COMMAND -> "command";
            case FILE_CHANGE -> "fileChange";
            case MCP -> "mcp";
            case WEB_SEARCH -> "webSearch";
            case UNKNOWN -> "unknown";
        };
        return "tool." + tool + "." + phase;
    }

    private static String limitOutput(String value) {
        if (value == null || value.length() <= TOOL_OUTPUT_CHUNK_LIMIT) return value;
        return value.substring(0, TOOL_OUTPUT_CHUNK_LIMIT) + "\n…[本次输出增量已截断]";
    }

    static String redact(String value) {
        if (value == null) return null;
        return SENSITIVE_ASSIGNMENT.matcher(value).replaceAll("$1$2***");
    }

    private static String messageOf(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return Objects.toString(cause.getMessage(), cause.getClass().getSimpleName());
    }

    @Override
    public void close() {
        CodexTurn turn = activeTurn.get();
        if (turn != null) {
            try {
                turn.interrupt();
            } catch (RuntimeException ignored) {
                // The Codex process may already be closing.
            }
        }
        pendingApprovals.values().forEach(future -> future.complete(ApprovalRequest.Decision.CANCEL));
        pendingApprovals.clear();
        watcher.close();
        channel.close();
    }

    private final class TurnEventSubscriber implements Flow.Subscriber<CodexEvent> {
        private final CodexTurn turn;
        private Flow.Subscription subscription;

        private TurnEventSubscriber(CodexTurn turn) {
            this.turn = turn;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(CodexEvent event) {
            try {
                route(event);
            } finally {
                subscription.request(1);
            }
        }

        @Override
        public void onError(Throwable error) {
            channel.publish("turn.streamError", Map.of("turnId", turn.id(), "message", messageOf(error)));
        }

        @Override
        public void onComplete() {
            channel.publish("turn.streamClosed", Map.of("turnId", turn.id()));
        }
    }
}
