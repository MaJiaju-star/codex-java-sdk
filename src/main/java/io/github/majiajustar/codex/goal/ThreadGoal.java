package io.github.majiajustar.codex.goal;

import com.fasterxml.jackson.databind.JsonNode;

/** app-server 为一个 Thread 持久化的长期目标及其用量。 */
public record ThreadGoal(
        String threadId,
        String objective,
        ThreadGoalStatus status,
        Long tokenBudget,
        long tokensUsed,
        long timeUsedSeconds,
        long createdAt,
        long updatedAt,
        JsonNode raw) {

    /** 从 v2 协议对象创建强类型目标。 */
    public static ThreadGoal from(JsonNode value) {
        return new ThreadGoal(
                value.path("threadId").asText(""),
                value.path("objective").asText(""),
                ThreadGoalStatus.fromWireValue(value.path("status").asText()),
                value.path("tokenBudget").isNumber() ? value.path("tokenBudget").asLong() : null,
                value.path("tokensUsed").asLong(),
                value.path("timeUsedSeconds").asLong(),
                value.path("createdAt").asLong(),
                value.path("updatedAt").asLong(),
                value);
    }
}
