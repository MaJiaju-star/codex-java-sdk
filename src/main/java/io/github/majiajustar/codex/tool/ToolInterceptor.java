package io.github.majiajustar.codex.tool;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 拦截受支持的工具审批，并观察工具最终完成结果。
 *
 * <p>工具调用前，拦截器按注册顺序运行，第一个 {@link BeforeResult.Decide} 决策生效；
 * 完成回调则按相反顺序运行。实现应避免阻塞，需要外部处理时应返回异步阶段。
 */
public interface ToolInterceptor {
    /**
     * 在调用已配置的 {@link ApprovalHandler} 前，可选择直接作出审批决策。
     *
     * @param request 审批请求
     * @return 包含 {@link BeforeResult.Continue} 或明确决策的异步阶段
     */
    default CompletionStage<BeforeResult> beforeToolCall(ApprovalRequest request) {
        return CompletableFuture.completedFuture(new BeforeResult.Continue());
    }

    /**
     * 观察已完成的工具调用；此时已不能再改变调用结果。
     *
     * @param result 已完成的工具结果
     * @return 后置处理结束时完成的异步阶段
     */
    default CompletionStage<Void> afterToolCall(ToolCallResult result) {
        return CompletableFuture.completedFuture(null);
    }

    /** {@link #beforeToolCall(ApprovalRequest)} 返回的结果。 */
    sealed interface BeforeResult permits BeforeResult.Continue, BeforeResult.Decide {
        /** 继续执行下一个拦截器或审批处理器。 */
        record Continue() implements BeforeResult {}

        /**
         * 立即确定审批结果。
         *
         * @param decision 返回给 app-server 的决策
         */
        record Decide(ApprovalRequest.Decision decision) implements BeforeResult {}
    }
}
