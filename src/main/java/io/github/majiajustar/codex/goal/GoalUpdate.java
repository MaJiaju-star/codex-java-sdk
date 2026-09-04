package io.github.majiajustar.codex.goal;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.majiajustar.codex.internal.JsonSupport;

/** 创建或更新 Thread Goal 时需要修改的字段。 */
public final class GoalUpdate {
    private final String objective;
    private final ThreadGoalStatus status;
    private final Long tokenBudget;

    private GoalUpdate(Builder builder) {
        objective = builder.objective;
        status = builder.status;
        tokenBudget = builder.tokenBudget;
        if (objective == null && status == null && tokenBudget == null) {
            throw new IllegalArgumentException("at least one goal field must be provided");
        }
        if (status == ThreadGoalStatus.UNKNOWN) {
            throw new IllegalArgumentException("UNKNOWN cannot be sent to app-server");
        }
        if (tokenBudget != null && tokenBudget <= 0) {
            throw new IllegalArgumentException("tokenBudget must be positive");
        }
    }

    /** 创建 Goal 更新 Builder。 */
    public static Builder builder() {
        return new Builder();
    }

    ObjectNode toJson(String threadId) {
        var json = JsonSupport.MAPPER.createObjectNode().put("threadId", threadId);
        if (objective != null) json.put("objective", objective);
        if (status != null) json.put("status", status.wireValue());
        if (tokenBudget != null) json.put("tokenBudget", tokenBudget);
        return json;
    }

    /** Goal 更新参数构建器。 */
    public static final class Builder {
        private String objective;
        private ThreadGoalStatus status;
        private Long tokenBudget;

        public Builder objective(String objective) {
            this.objective = objective;
            return this;
        }

        public Builder status(ThreadGoalStatus status) {
            this.status = status;
            return this;
        }

        public Builder tokenBudget(long tokenBudget) {
            this.tokenBudget = tokenBudget;
            return this;
        }

        public GoalUpdate build() {
            return new GoalUpdate(this);
        }
    }
}
