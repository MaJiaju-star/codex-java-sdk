package io.github.majiajustar.codex.event;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 已知的 app-server 通知方法。
 *
 * <p>当新版 app-server 发出当前 SDK 尚未识别的方法时，{@link #UNKNOWN} 可保持前向兼容。
 * 调用方始终可以通过 {@link CodexEvent#method()} 检查原始方法名。
 */
public enum CodexEventType {
    TURN_STARTED("turn/started"),
    TURN_COMPLETED("turn/completed"),
    ITEM_STARTED("item/started"),
    ITEM_COMPLETED("item/completed"),
    AGENT_MESSAGE_DELTA("item/agentMessage/delta"),
    REASONING_TEXT_DELTA("item/reasoning/textDelta"),
    REASONING_SUMMARY_TEXT_DELTA("item/reasoning/summaryTextDelta"),
    REASONING_SUMMARY_PART_ADDED("item/reasoning/summaryPartAdded"),
    COMMAND_OUTPUT_DELTA("item/commandExecution/outputDelta"),
    TERMINAL_INTERACTION("item/commandExecution/terminalInteraction"),
    FILE_CHANGE_PATCH_UPDATED("item/fileChange/patchUpdated"),
    MCP_TOOL_CALL_PROGRESS("item/mcpToolCall/progress"),
    MCP_SERVER_STATUS_UPDATED("mcpServer/startupStatus/updated"),
    MCP_SERVER_OAUTH_LOGIN_COMPLETED("mcpServer/oauthLogin/completed"),
    PLAN_DELTA("item/plan/delta"),
    TOKEN_USAGE_UPDATED("thread/tokenUsage/updated"),
    THREAD_STARTED("thread/started"),
    THREAD_STATUS_CHANGED("thread/status/changed"),
    THREAD_ARCHIVED("thread/archived"),
    THREAD_UNARCHIVED("thread/unarchived"),
    THREAD_DELETED("thread/deleted"),
    THREAD_CLOSED("thread/closed"),
    THREAD_REVERTED("thread/reverted"),
    THREAD_NAME_UPDATED("thread/name/updated"),
    THREAD_GOAL_UPDATED("thread/goal/updated"),
    THREAD_GOAL_CLEARED("thread/goal/cleared"),
    SKILLS_CHANGED("skills/changed"),
    TURN_DIFF_UPDATED("turn/diff/updated"),
    TURN_PLAN_UPDATED("turn/plan/updated"),
    SERVER_REQUEST_RESOLVED("serverRequest/resolved"),
    CONTEXT_COMPACTED("thread/compacted"),
    WARNING("warning"),
    CONFIG_WARNING("configWarning"),
    DEPRECATION_NOTICE("deprecationNotice"),
    MODEL_REROUTED("model/rerouted"),
    ERROR("error"),
    UNKNOWN(null);

    private static final Map<String, CodexEventType> BY_METHOD = Arrays.stream(values())
            .filter(value -> value.method != null)
            .collect(Collectors.toUnmodifiableMap(CodexEventType::method, Function.identity()));

    private final String method;

    CodexEventType(String method) {
        this.method = method;
    }

    /** 返回准确的 JSON-RPC 通知方法名；{@link #UNKNOWN} 对应 {@code null}。 */
    public String method() {
        return method;
    }

    /**
     * 对 JSON-RPC 通知方法进行分类。
     *
     * @param method 从 app-server 收到的准确方法名
     * @return 匹配的类型；无法识别时返回 {@link #UNKNOWN}
     */
    public static CodexEventType fromMethod(String method) {
        return BY_METHOD.getOrDefault(method, UNKNOWN);
    }
}
