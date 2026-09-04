package io.github.majiajustar.codex.event;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.majiajustar.codex.generated.v2.ThreadStatus;
import io.github.majiajustar.codex.generated.v2.Turn;
import io.github.majiajustar.codex.generated.v2.TurnError;
import io.github.majiajustar.codex.goal.ThreadGoal;
import io.github.majiajustar.codex.internal.JsonSupport;
import io.github.majiajustar.codex.mcp.McpServerStartupState;
import io.github.majiajustar.codex.turn.TokenUsage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * app-server 高频通知的强类型视图，主要供交互式客户端消费。
 *
 * <p>尚未支持的通知使用 {@link Unknown} 表示。当协议新增通知或字段时，调用方仍可通过
 * {@link #raw()} 获取未经裁剪的原始参数。
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
    /**
     * 返回 SDK 已识别的通知类型。
     *
     * @return 通知类型；未识别的通知返回 {@link CodexEventType#UNKNOWN}
     */
    CodexEventType type();

    /**
     * 返回准确的 JSON-RPC 通知方法名。
     *
     * @return 协议方法名；未知通知返回 app-server 实际发送的方法名
     */
    default String method() {
        return type().method();
    }

    /**
     * 返回完整的原始通知参数，以保留 SDK 尚未声明的字段。
     *
     * @return 原始通知参数
     */
    JsonNode raw();

    /**
     * 将原始事件解析为当前 SDK 能识别的最具体通知类型。
     *
     * @param event app-server 原始事件
     * @return 强类型通知；无法识别时返回 {@link Unknown}
     */
    static CodexNotification from(CodexEvent event) {
        var params = event.params();
        return switch (event.type()) {
            case TURN_STARTED -> new TurnStarted(
                    text(params, "threadId"), decode(params.path("turn"), Turn.class), params);
            case TURN_COMPLETED -> new TurnCompleted(
                    text(params, "threadId"), decode(params.path("turn"), Turn.class), params);
            case ITEM_STARTED -> new ItemStarted(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    CodexItem.from(params.path("item")),
                    params.path("startedAtMs").asLong(),
                    params);
            case ITEM_COMPLETED -> new ItemCompleted(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    CodexItem.from(params.path("item")),
                    params.path("completedAtMs").asLong(),
                    params);
            case AGENT_MESSAGE_DELTA,
                    PLAN_DELTA,
                    REASONING_TEXT_DELTA,
                    REASONING_SUMMARY_TEXT_DELTA,
                    COMMAND_OUTPUT_DELTA -> new Delta(
                    event.type(),
                    text(params, "threadId"),
                    text(params, "turnId"),
                    text(params, "itemId"),
                    text(params, "delta"),
                    nullableLong(params, "contentIndex"),
                    nullableLong(params, "summaryIndex"),
                    params);
            case FILE_CHANGE_PATCH_UPDATED -> new FileChangePatchUpdated(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    text(params, "itemId"),
                    CodexItem.fileUpdates(params.path("changes")),
                    params);
            case MCP_TOOL_CALL_PROGRESS -> new McpToolCallProgress(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    text(params, "itemId"),
                    text(params, "message"),
                    params);
            case MCP_SERVER_STATUS_UPDATED -> new McpServerStatusUpdated(
                    nullableText(params, "threadId"),
                    text(params, "name"),
                    McpServerStartupState.fromWireValue(text(params, "status")),
                    nullableText(params, "error"),
                    nullableText(params, "failureReason"),
                    params);
            case MCP_SERVER_OAUTH_LOGIN_COMPLETED -> new McpServerOAuthLoginCompleted(
                    text(params, "name"),
                    nullableText(params, "threadId"),
                    params.path("success").asBoolean(),
                    nullableText(params, "error"),
                    params);
            case TOKEN_USAGE_UPDATED -> new TokenUsageUpdated(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    TokenUsage.from(params.path("tokenUsage")),
                    params);
            case THREAD_STARTED -> new ThreadStarted(
                    decode(params.path("thread"), io.github.majiajustar.codex.generated.v2.Thread.class),
                    params);
            case THREAD_STATUS_CHANGED -> new ThreadStatusChanged(
                    text(params, "threadId"),
                    decode(params.path("status"), ThreadStatus.class),
                    params);
            case THREAD_ARCHIVED,
                    THREAD_UNARCHIVED,
                    THREAD_DELETED,
                    THREAD_CLOSED,
                    THREAD_REVERTED ->
                new ThreadLifecycle(event.type(), text(params, "threadId"), params);
            case THREAD_NAME_UPDATED -> new ThreadNameUpdated(
                    text(params, "threadId"), nullableText(params, "threadName"), params);
            case THREAD_GOAL_UPDATED -> new ThreadGoalUpdated(
                    text(params, "threadId"),
                    nullableText(params, "turnId"),
                    ThreadGoal.from(params.path("goal")),
                    params);
            case THREAD_GOAL_CLEARED -> new ThreadGoalCleared(text(params, "threadId"), params);
            case SKILLS_CHANGED -> new SkillsChanged(params);
            case TURN_DIFF_UPDATED -> new TurnDiffUpdated(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    text(params, "diff"),
                    params);
            case TURN_PLAN_UPDATED -> new TurnPlanUpdated(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    nullableText(params, "explanation"),
                    planSteps(params.path("plan")),
                    params);
            case REASONING_SUMMARY_PART_ADDED -> new ReasoningSummaryPartAdded(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    text(params, "itemId"),
                    params.path("summaryIndex").asInt(),
                    params);
            case TERMINAL_INTERACTION -> new TerminalInteraction(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    text(params, "itemId"),
                    text(params, "processId"),
                    text(params, "stdin"),
                    params);
            case SERVER_REQUEST_RESOLVED -> new ServerRequestResolved(
                    text(params, "threadId"), text(params, "requestId"), params);
            case CONTEXT_COMPACTED -> new ContextCompacted(
                    text(params, "threadId"), text(params, "turnId"), params);
            case WARNING ->
                new Warning(nullableText(params, "threadId"), text(params, "message"), params);
            case CONFIG_WARNING -> new ConfigWarning(
                    text(params, "summary"),
                    nullableText(params, "details"),
                    nullablePath(params, "path"),
                    textRange(params.path("range")),
                    params);
            case DEPRECATION_NOTICE -> new DeprecationNotice(
                    text(params, "summary"), nullableText(params, "details"), params);
            case MODEL_REROUTED -> new ModelRerouted(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    text(params, "fromModel"),
                    text(params, "toModel"),
                    text(params, "reason"),
                    params);
            case ERROR -> new Error(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    params.path("willRetry").asBoolean(),
                    decode(params.path("error"), TurnError.class),
                    params);
            case UNKNOWN -> new Unknown(event.method(), params);
        };
    }

    /**
     * Turn 已进入运行状态。
     *
     * @param threadId 所属 Thread ID
     * @param turn Turn 的当前状态
     * @param raw 完整原始通知参数
     */
    record TurnStarted(String threadId, Turn turn, JsonNode raw) implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.TURN_STARTED;
        }
    }

    /**
     * Turn 已到达终态。
     *
     * @param threadId 所属 Thread ID
     * @param turn Turn 的最终状态
     * @param raw 完整原始通知参数
     */
    record TurnCompleted(String threadId, Turn turn, JsonNode raw) implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.TURN_COMPLETED;
        }
    }

    /**
     * Item 生命周期已开始。
     *
     * @param threadId 所属 Thread ID
     * @param turnId 所属 Turn ID
     * @param item 已开始的 Item
     * @param startedAtMs 开始时间，使用 Unix 毫秒时间戳
     * @param raw 完整原始通知参数
     */
    record ItemStarted(String threadId, String turnId, CodexItem item, long startedAtMs, JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.ITEM_STARTED;
        }
    }

    /**
     * Item 生命周期已完成。
     *
     * @param threadId 所属 Thread ID
     * @param turnId 所属 Turn ID
     * @param item 已完成的 Item
     * @param completedAtMs 完成时间，使用 Unix 毫秒时间戳
     * @param raw 完整原始通知参数
     */
    record ItemCompleted(
            String threadId, String turnId, CodexItem item, long completedAtMs, JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.ITEM_COMPLETED;
        }
    }

    /**
     * Item 对应的文本或命令输出增量。
     *
     * @param type 增量通知类型
     * @param threadId 所属 Thread ID
     * @param turnId 所属 Turn ID
     * @param itemId 所属 Item ID
     * @param delta 本次追加的文本
     * @param contentIndex 推理正文分段索引；其他增量为 {@code null}
     * @param summaryIndex 推理摘要分段索引；其他增量为 {@code null}
     * @param raw 完整原始通知参数
     */
    record Delta(
            CodexEventType type,
            String threadId,
            String turnId,
            String itemId,
            String delta,
            Long contentIndex,
            Long summaryIndex,
            JsonNode raw)
            implements CodexNotification {}

    /**
     * 正在执行的文件变更 Item 所对应的最新结构化补丁。
     *
     * @param threadId 所属 Thread ID
     * @param turnId 所属 Turn ID
     * @param itemId 所属 Item ID
     * @param changes 最新文件变更列表
     * @param raw 完整原始通知参数
     */
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
        public CodexEventType type() {
            return CodexEventType.FILE_CHANGE_PATCH_UPDATED;
        }
    }

    /**
     * MCP 工具调用产生的可读进度信息。
     *
     * @param threadId 所属 Thread ID
     * @param turnId 所属 Turn ID
     * @param itemId 所属 Item ID
     * @param message 进度说明
     * @param raw 完整原始通知参数
     */
    record McpToolCallProgress(
            String threadId, String turnId, String itemId, String message, JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.MCP_TOOL_CALL_PROGRESS;
        }
    }

    /**
     * MCP Server 启动或重新连接时的状态变化。
     *
     * @param threadId 可选的所属 Thread ID
     * @param name MCP Server 名称
     * @param status 启动状态
     * @param error 可选的错误信息
     * @param failureReason 可选的失败原因
     * @param raw 完整原始通知参数
     */
    record McpServerStatusUpdated(
            String threadId,
            String name,
            McpServerStartupState status,
            String error,
            String failureReason,
            JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.MCP_SERVER_STATUS_UPDATED;
        }
    }

    /**
     * MCP OAuth 登录流程完成。
     *
     * @param name MCP Server 名称
     * @param threadId 可选的所属 Thread ID
     * @param successful 是否登录成功
     * @param error 可选的错误信息
     * @param raw 完整原始通知参数
     */
    record McpServerOAuthLoginCompleted(
            String name, String threadId, boolean successful, String error, JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.MCP_SERVER_OAUTH_LOGIN_COMPLETED;
        }
    }

    /**
     * 累计用量及最近一次 Turn 的 Token 用量更新。
     *
     * @param threadId 所属 Thread ID
     * @param turnId 所属 Turn ID
     * @param usage Token 用量
     * @param raw 完整原始通知参数
     */
    record TokenUsageUpdated(String threadId, String turnId, TokenUsage usage, JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.TOKEN_USAGE_UPDATED;
        }
    }

    /**
     * 新 Thread 已可用。
     *
     * @param thread 新建的 Thread
     * @param raw 完整原始通知参数
     */
    record ThreadStarted(io.github.majiajustar.codex.generated.v2.Thread thread, JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.THREAD_STARTED;
        }
    }

    /**
     * Thread 活动状态发生变化。
     *
     * @param threadId Thread ID
     * @param status 最新活动状态
     * @param raw 完整原始通知参数
     */
    record ThreadStatusChanged(String threadId, ThreadStatus status, JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.THREAD_STATUS_CHANGED;
        }
    }

    /**
     * Thread 发生归档、删除、关闭或回退等持久化生命周期变化。
     *
     * @param type 生命周期通知类型
     * @param threadId Thread ID
     * @param raw 完整原始通知参数
     */
    record ThreadLifecycle(CodexEventType type, String threadId, JsonNode raw)
            implements CodexNotification {}

    /**
     * Thread 的用户可见名称发生变化。
     *
     * @param threadId Thread ID
     * @param threadName 最新名称；清除名称时为 {@code null}
     * @param raw 完整原始通知参数
     */
    record ThreadNameUpdated(String threadId, String threadName, JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.THREAD_NAME_UPDATED;
        }
    }

    /**
     * Thread 目标被创建、修改，或其用量发生变化。
     *
     * @param threadId Thread ID
     * @param turnId 可选的关联 Turn ID
     * @param goal 最新目标
     * @param raw 完整原始通知参数
     */
    record ThreadGoalUpdated(String threadId, String turnId, ThreadGoal goal, JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.THREAD_GOAL_UPDATED;
        }
    }

    /**
     * Thread 目标已被清除。
     *
     * @param threadId Thread ID
     * @param raw 完整原始通知参数
     */
    record ThreadGoalCleared(String threadId, JsonNode raw) implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.THREAD_GOAL_CLEARED;
        }
    }

    /**
     * Skill 文件变化导致客户端缓存失效。
     *
     * @param raw 完整原始通知参数
     */
    record SkillsChanged(JsonNode raw) implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.SKILLS_CHANGED;
        }
    }

    /**
     * 当前 Turn 的聚合差异内容发生变化。
     *
     * @param threadId 所属 Thread ID
     * @param turnId Turn ID
     * @param diff 最新聚合差异内容
     * @param raw 完整原始通知参数
     */
    record TurnDiffUpdated(String threadId, String turnId, String diff, JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.TURN_DIFF_UPDATED;
        }
    }

    /**
     * 当前 Turn 的执行计划发生变化。
     *
     * @param threadId 所属 Thread ID
     * @param turnId Turn ID
     * @param explanation 可选的计划说明
     * @param plan 最新计划步骤
     * @param raw 完整原始通知参数
     */
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
        public CodexEventType type() {
            return CodexEventType.TURN_PLAN_UPDATED;
        }
    }

    /**
     * 一项计划步骤及其当前状态。
     *
     * @param step 步骤说明
     * @param status 当前状态
     */
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

    /**
     * 推理 Item 增加了新的摘要分段。
     *
     * @param threadId 所属 Thread ID
     * @param turnId 所属 Turn ID
     * @param itemId 所属 Item ID
     * @param summaryIndex 新增摘要分段的索引
     * @param raw 完整原始通知参数
     */
    record ReasoningSummaryPartAdded(
            String threadId, String turnId, String itemId, int summaryIndex, JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.REASONING_SUMMARY_PART_ADDED;
        }
    }

    /**
     * 运行中命令收到了终端输入。
     *
     * @param threadId 所属 Thread ID
     * @param turnId 所属 Turn ID
     * @param itemId 所属 Item ID
     * @param processId 进程 ID
     * @param stdin 写入终端的内容
     * @param raw 完整原始通知参数
     */
    record TerminalInteraction(
            String threadId,
            String turnId,
            String itemId,
            String processId,
            String stdin,
            JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.TERMINAL_INTERACTION;
        }
    }

    /**
     * 一个服务端请求已经被当前或其他客户端解决。
     *
     * @param threadId 所属 Thread ID
     * @param requestId 服务端请求 ID
     * @param raw 完整原始通知参数
     */
    record ServerRequestResolved(String threadId, String requestId, JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.SERVER_REQUEST_RESOLVED;
        }
    }

    /**
     * Thread 上下文已完成服务器端压缩。
     *
     * @param threadId Thread ID
     * @param turnId 触发压缩的 Turn ID
     * @param raw 完整原始通知参数
     */
    record ContextCompacted(String threadId, String turnId, JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.CONTEXT_COMPACTED;
        }
    }

    /**
     * 与 Thread 可选关联的普通运行时警告。
     *
     * @param threadId 可选的 Thread ID
     * @param message 警告信息
     * @param raw 完整原始通知参数
     */
    record Warning(String threadId, String message, JsonNode raw) implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.WARNING;
        }
    }

    /**
     * 配置文件中的位置；行号和列号均从 1 开始计数。
     *
     * @param line 行号
     * @param column 列号
     */
    record TextPosition(int line, int column) {}

    /**
     * 配置警告对应的文件范围。
     *
     * @param start 起始位置
     * @param end 结束位置
     */
    record TextRange(TextPosition start, TextPosition end) {}

    /**
     * 配置加载或校验警告。
     *
     * @param summary 警告摘要
     * @param details 可选的详细说明
     * @param path 可选的配置文件路径
     * @param range 可选的文件位置范围
     * @param raw 完整原始通知参数
     */
    record ConfigWarning(
            String summary, String details, Path path, TextRange range, JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.CONFIG_WARNING;
        }
    }

    /**
     * 客户端正在使用即将移除的协议或配置。
     *
     * @param summary 弃用摘要
     * @param details 可选的详细说明
     * @param raw 完整原始通知参数
     */
    record DeprecationNotice(String summary, String details, JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.DEPRECATION_NOTICE;
        }
    }

    /**
     * 当前 Turn 因安全或能力原因被切换到另一模型。
     *
     * @param threadId 所属 Thread ID
     * @param turnId Turn ID
     * @param fromModel 原模型
     * @param toModel 目标模型
     * @param reason 切换原因
     * @param raw 完整原始通知参数
     */
    record ModelRerouted(
            String threadId,
            String turnId,
            String fromModel,
            String toModel,
            String reason,
            JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.MODEL_REROUTED;
        }
    }

    /**
     * 与特定 Turn 关联的 app-server 错误通知。
     *
     * @param threadId 所属 Thread ID
     * @param turnId Turn ID
     * @param willRetry app-server 是否会自动重试
     * @param error 强类型错误详情
     * @param raw 完整原始通知参数
     */
    record Error(String threadId, String turnId, boolean willRetry, TurnError error, JsonNode raw)
            implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.ERROR;
        }
    }

    /**
     * 当前 SDK 版本尚未识别的通知。
     *
     * @param method app-server 实际发送的方法名
     * @param raw 完整原始通知参数
     */
    record Unknown(String method, JsonNode raw) implements CodexNotification {
        @Override
        public CodexEventType type() {
            return CodexEventType.UNKNOWN;
        }
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private static String nullableText(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Long nullableLong(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.longValue();
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

}
