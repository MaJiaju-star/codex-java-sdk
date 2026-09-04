package io.github.majiajustar.codex.goal;

import io.github.majiajustar.codex.CodexClient;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.util.Objects;
import java.util.Optional;

/** 操作单个 Thread 上持久化 Goal 的强类型客户端。 */
public final class ThreadGoals {
    private final CodexClient codex;
    private final String threadId;

    public ThreadGoals(CodexClient codex, String threadId) {
        this.codex = Objects.requireNonNull(codex, "codex");
        this.threadId = Objects.requireNonNull(threadId, "threadId");
    }

    /** 创建或更新目标，并返回服务器保存后的完整状态。 */
    public ThreadGoal set(GoalUpdate update) {
        Objects.requireNonNull(update, "update");
        var result = codex.request("thread/goal/set", update.toJson(threadId));
        return ThreadGoal.from(result.path("goal"));
    }

    /** 读取当前目标；该 Thread 尚未设置目标时返回空。 */
    public Optional<ThreadGoal> get() {
        var params = JsonSupport.MAPPER.createObjectNode().put("threadId", threadId);
        var goal = codex.request("thread/goal/get", params).path("goal");
        return goal.isMissingNode() || goal.isNull()
                ? Optional.empty()
                : Optional.of(ThreadGoal.from(goal));
    }

    /** 清除当前目标，并返回服务器是否实际清除了目标。 */
    public boolean clear() {
        var params = JsonSupport.MAPPER.createObjectNode().put("threadId", threadId);
        return codex.request("thread/goal/clear", params).path("cleared").asBoolean();
    }
}
