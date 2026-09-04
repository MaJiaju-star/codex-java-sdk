package io.github.majiajustar.codex.event;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.majiajustar.codex.generated.v2.ThreadStatus;
import io.github.majiajustar.codex.generated.v2.Turn;
import io.github.majiajustar.codex.goal.ThreadGoal;
import io.github.majiajustar.codex.internal.JsonSupport;
import io.github.majiajustar.codex.mcp.McpServerStartupState;
import io.github.majiajustar.codex.turn.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;

/**
 * Typed view of the high-frequency app-server notifications used by interactive clients.
 *
 * <p>Unsupported methods are represented by {@link Unknown}; callers never lose access to the raw
 * parameters when the protocol adds a notification.
 */
public sealed interface CodexNotification
        permits CodexNotification.TurnStarted,
                CodexNotification.TurnCompleted,
                CodexNotification.ItemStarted,
                CodexNotification.ItemCompleted,
                CodexNotification.Delta,
                CodexNotification.FileChangePatchUpdated,
                CodexNotification.McpToolCallProgress,
                CodexNotification.McpServerStatusUpdated,
                CodexNotification.McpServerOAuthLoginCompleted,
                CodexNotification.TokenUsageUpdated,
                CodexNotification.ThreadStarted,
                CodexNotification.ThreadStatusChanged,
                CodexNotification.ThreadLifecycle,
                CodexNotification.ThreadNameUpdated,
                CodexNotification.ThreadGoalUpdated,
                CodexNotification.ThreadGoalCleared,
                CodexNotification.SkillsChanged,
                CodexNotification.TurnDiffUpdated,
                CodexNotification.TurnPlanUpdated,
                CodexNotification.ReasoningSummaryPartAdded,
                CodexNotification.TerminalInteraction,
                CodexNotification.ServerRequestResolved,
                CodexNotification.ContextCompacted,
                CodexNotification.Warning,
                CodexNotification.ConfigWarning,
                CodexNotification.DeprecationNotice,
                CodexNotification.ModelRerouted,
                CodexNotification.Error,
                CodexNotification.Unknown {
    /** Exact notification method. */
    String method();

    /** Original notification parameters. */
    JsonNode raw();

    /** Parse the strongest known notification type for an event. */
    static CodexNotification from(CodexEvent event) {
        var params = event.params();
        return switch (event.method()) {
            case "turn/started" -> new TurnStarted(
                    text(params, "threadId"), decode(params.path("turn"), Turn.class), params);
            case "turn/completed" -> new TurnCompleted(
                    text(params, "threadId"), decode(params.path("turn"), Turn.class), params);
            case "item/started" -> new ItemStarted(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    CodexItem.from(params.path("item")),
                    params.path("startedAtMs").asLong(),
                    params);
            case "item/completed" -> new ItemCompleted(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    CodexItem.from(params.path("item")),
                    params.path("completedAtMs").asLong(),
                    params);
            case "item/agentMessage/delta",
                    "item/plan/delta",
                    "item/reasoning/textDelta",
                    "item/reasoning/summaryTextDelta",
                    "item/commandExecution/outputDelta" -> new Delta(
                    event.method(),
                    text(params, "threadId"),
                    text(params, "turnId"),
                    text(params, "itemId"),
                    text(params, "delta"),
                    params);
            case "item/fileChange/patchUpdated" -> new FileChangePatchUpdated(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    text(params, "itemId"),
                    fileUpdates(params.path("changes")),
                    params);
            case "item/mcpToolCall/progress" -> new McpToolCallProgress(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    text(params, "itemId"),
                    text(params, "message"),
                    params);
            case "mcpServer/startupStatus/updated" -> new McpServerStatusUpdated(
                    nullableText(params, "threadId"),
                    text(params, "name"),
                    McpServerStartupState.fromWireValue(text(params, "status")),
                    nullableText(params, "error"),
                    nullableText(params, "failureReason"),
                    params);
            case "mcpServer/oauthLogin/completed" -> new McpServerOAuthLoginCompleted(
                    text(params, "name"),
                    nullableText(params, "threadId"),
                    params.path("success").asBoolean(),
                    nullableText(params, "error"),
                    params);
            case "thread/tokenUsage/updated" -> new TokenUsageUpdated(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    TokenUsage.from(params.path("tokenUsage")),
                    params);
            case "thread/started" -> new ThreadStarted(
                    decode(params.path("thread"), io.github.majiajustar.codex.generated.v2.Thread.class),
                    params);
            case "thread/status/changed" -> new ThreadStatusChanged(
                    text(params, "threadId"),
                    decode(params.path("status"), ThreadStatus.class),
                    params);
            case "thread/archived",
                    "thread/unarchived",
                    "thread/deleted",
                    "thread/closed",
                    "thread/reverted" ->
                new ThreadLifecycle(event.method(), text(params, "threadId"), params);
            case "thread/name/updated" -> new ThreadNameUpdated(
                    text(params, "threadId"), nullableText(params, "threadName"), params);
            case "thread/goal/updated" -> new ThreadGoalUpdated(
                    text(params, "threadId"),
                    nullableText(params, "turnId"),
                    ThreadGoal.from(params.path("goal")),
                    params);
            case "thread/goal/cleared" -> new ThreadGoalCleared(text(params, "threadId"), params);
            case "skills/changed" -> new SkillsChanged(params);
            case "turn/diff/updated" -> new TurnDiffUpdated(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    text(params, "diff"),
                    params);
            case "turn/plan/updated" -> new TurnPlanUpdated(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    nullableText(params, "explanation"),
                    planSteps(params.path("plan")),
                    params);
            case "item/reasoning/summaryPartAdded" -> new ReasoningSummaryPartAdded(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    text(params, "itemId"),
                    params.path("summaryIndex").asInt(),
                    params);
            case "item/commandExecution/terminalInteraction" -> new TerminalInteraction(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    text(params, "itemId"),
                    text(params, "processId"),
                    text(params, "stdin"),
                    params);
            case "serverRequest/resolved" -> new ServerRequestResolved(
                    text(params, "threadId"), text(params, "requestId"), params);
            case "thread/compacted" -> new ContextCompacted(
                    text(params, "threadId"), text(params, "turnId"), params);
            case "warning" ->
                new Warning(nullableText(params, "threadId"), text(params, "message"), params);
            case "configWarning" -> new ConfigWarning(
                    text(params, "summary"),
                    nullableText(params, "details"),
                    nullablePath(params, "path"),
                    textRange(params.path("range")),
                    params);
            case "deprecationNotice" -> new DeprecationNotice(
                    text(params, "summary"), nullableText(params, "details"), params);
            case "model/rerouted" -> new ModelRerouted(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    text(params, "fromModel"),
                    text(params, "toModel"),
                    text(params, "reason"),
                    params);
            case "error" -> new Error(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    params.path("willRetry").asBoolean(),
                    params.path("error"),
                    params);
            default -> new Unknown(event.method(), params);
        };
    }

    /** Turn entered the running state. */
    record TurnStarted(String threadId, Turn turn, JsonNode raw) implements CodexNotification {
        @Override
        public String method() {
            return "turn/started";
        }
    }

    /** Turn reached a terminal state. */
    record TurnCompleted(String threadId, Turn turn, JsonNode raw) implements CodexNotification {
        @Override
        public String method() {
            return "turn/completed";
        }
    }

    /** Item lifecycle started. */
    record ItemStarted(String threadId, String turnId, CodexItem item, long startedAtMs, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "item/started";
        }
    }

    /** Item lifecycle completed. */
    record ItemCompleted(
            String threadId, String turnId, CodexItem item, long completedAtMs, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "item/completed";
        }
    }

    /** Text or command-output delta associated with one item. */
    record Delta(
            String method,
            String threadId,
            String turnId,
            String itemId,
            String delta,
            JsonNode raw)
            implements CodexNotification {}

    /** Updated structured patch for an in-flight file-change item. */
    record FileChangePatchUpdated(
            String threadId,
            String turnId,
            String itemId,
            List<CodexItem.FileUpdate> changes,
            JsonNode raw)
            implements CodexNotification {
        public FileChangePatchUpdated {
            changes = List.copyOf(changes);
        }

        @Override
        public String method() {
            return "item/fileChange/patchUpdated";
        }
    }

    /** Human-readable progress from an MCP tool invocation. */
    record McpToolCallProgress(
            String threadId, String turnId, String itemId, String message, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "item/mcpToolCall/progress";
        }
    }

    /** MCP Server 启动或重新连接时的状态变化。 */
    record McpServerStatusUpdated(
            String threadId,
            String name,
            McpServerStartupState status,
            String error,
            String failureReason,
            JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "mcpServer/startupStatus/updated";
        }
    }

    /** MCP OAuth 登录流程完成。 */
    record McpServerOAuthLoginCompleted(
            String name, String threadId, boolean successful, String error, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "mcpServer/oauthLogin/completed";
        }
    }

    /** Updated cumulative and last-turn token accounting. */
    record TokenUsageUpdated(String threadId, String turnId, TokenUsage usage, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "thread/tokenUsage/updated";
        }
    }

    /** New thread became available. */
    record ThreadStarted(io.github.majiajustar.codex.generated.v2.Thread thread, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "thread/started";
        }
    }

    /** Thread activity status changed. */
    record ThreadStatusChanged(String threadId, ThreadStatus status, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "thread/status/changed";
        }
    }

    /** Thread 的归档、删除、关闭或回退等持久化生命周期变化。 */
    record ThreadLifecycle(String method, String threadId, JsonNode raw)
            implements CodexNotification {}

    /** Thread 的用户可见名称发生变化。 */
    record ThreadNameUpdated(String threadId, String threadName, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "thread/name/updated";
        }
    }

    /** Thread Goal 被创建、修改，或用量发生变化。 */
    record ThreadGoalUpdated(String threadId, String turnId, ThreadGoal goal, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "thread/goal/updated";
        }
    }

    /** Thread Goal 已被清除。 */
    record ThreadGoalCleared(String threadId, JsonNode raw) implements CodexNotification {
        @Override
        public String method() {
            return "thread/goal/cleared";
        }
    }

    /** Skill 文件变化导致客户端缓存失效。 */
    record SkillsChanged(JsonNode raw) implements CodexNotification {
        @Override
        public String method() {
            return "skills/changed";
        }
    }

    /** 当前 Turn 的聚合 diff 发生变化。 */
    record TurnDiffUpdated(String threadId, String turnId, String diff, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "turn/diff/updated";
        }
    }

    /** 当前 Turn 的执行计划发生变化。 */
    record TurnPlanUpdated(
            String threadId,
            String turnId,
            String explanation,
            List<PlanStep> plan,
            JsonNode raw)
            implements CodexNotification {
        public TurnPlanUpdated {
            plan = List.copyOf(plan);
        }

        @Override
        public String method() {
            return "turn/plan/updated";
        }
    }

    /** 一项计划步骤及其当前状态。 */
    record PlanStep(String step, PlanStepStatus status) {}

    /** app-server 已知的计划步骤状态。 */
    enum PlanStepStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        UNKNOWN;

        static PlanStepStatus fromWireValue(String value) {
            return switch (value) {
                case "pending" -> PENDING;
                case "inProgress" -> IN_PROGRESS;
                case "completed" -> COMPLETED;
                default -> UNKNOWN;
            };
        }
    }

    /** Reasoning Item 增加了新的摘要分段。 */
    record ReasoningSummaryPartAdded(
            String threadId, String turnId, String itemId, int summaryIndex, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "item/reasoning/summaryPartAdded";
        }
    }

    /** 运行中命令收到了终端输入。 */
    record TerminalInteraction(
            String threadId,
            String turnId,
            String itemId,
            String processId,
            String stdin,
            JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "item/commandExecution/terminalInteraction";
        }
    }

    /** 一个服务端审批请求已经被当前或其他客户端解决。 */
    record ServerRequestResolved(String threadId, String requestId, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "serverRequest/resolved";
        }
    }

    /** Thread 上下文已完成服务器端压缩。 */
    record ContextCompacted(String threadId, String turnId, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "thread/compacted";
        }
    }

    /** 与 Thread 可选关联的普通运行时警告。 */
    record Warning(String threadId, String message, JsonNode raw) implements CodexNotification {
        @Override
        public String method() {
            return "warning";
        }
    }

    /** 配置文件中的位置。行列均为 1-based。 */
    record TextPosition(int line, int column) {}

    /** 配置警告对应的文件范围。 */
    record TextRange(TextPosition start, TextPosition end) {}

    /** 配置加载或校验警告。 */
    record ConfigWarning(
            String summary, String details, Path path, TextRange range, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "configWarning";
        }
    }

    /** 客户端正在使用即将移除的协议或配置。 */
    record DeprecationNotice(String summary, String details, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "deprecationNotice";
        }
    }

    /** 当前 Turn 因安全或能力原因被切换到另一模型。 */
    record ModelRerouted(
            String threadId,
            String turnId,
            String fromModel,
            String toModel,
            String reason,
            JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "model/rerouted";
        }
    }

    /** Turn-scoped app-server error notification. */
    record Error(String threadId, String turnId, boolean willRetry, JsonNode error, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "error";
        }
    }

    /** Notification method unknown to this SDK version. */
    record Unknown(String method, JsonNode raw) implements CodexNotification {}

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private static String nullableText(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Path nullablePath(JsonNode node, String field) {
        var value = nullableText(node, field);
        return value == null ? null : Path.of(value);
    }

    private static TextRange textRange(JsonNode value) {
        if (!value.isObject()) return null;
        return new TextRange(textPosition(value.path("start")), textPosition(value.path("end")));
    }

    private static TextPosition textPosition(JsonNode value) {
        return new TextPosition(value.path("line").asInt(), value.path("column").asInt());
    }

    private static List<PlanStep> planSteps(JsonNode values) {
        if (!values.isArray()) return List.of();
        var result = new ArrayList<PlanStep>();
        values.forEach(value -> result.add(new PlanStep(
                text(value, "step"),
                PlanStepStatus.fromWireValue(text(value, "status")))));
        return List.copyOf(result);
    }

    private static <T> T decode(JsonNode value, Class<T> type) {
        return JsonSupport.MAPPER.convertValue(value, type);
    }

    private static List<CodexItem.FileUpdate> fileUpdates(JsonNode values) {
        if (!values.isArray()) return List.of();
        var result = new ArrayList<CodexItem.FileUpdate>();
        values.forEach(value -> result.add(new CodexItem.FileUpdate(
                text(value, "path"), text(value, "kind"), text(value, "diff"))));
        return List.copyOf(result);
    }
}
