package io.github.majiajustar.codex.tool;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 决定是否允许 Codex 执行命令或应用文件变更。
 *
 * <p>app-server 会等待返回的异步阶段，因此实现可以异步询问用户或外部策略服务。若异步阶段以异常结束，
 * SDK 会将该请求视为拒绝。
 */
@FunctionalInterface
public interface ApprovalHandler {
    /**
     * 请求对单次工具操作作出决策。
     *
     * @param request 强类型审批请求及其原始协议载荷
     * @return 最终产生审批决策的异步阶段
     */
    CompletionStage<ApprovalRequest.Decision> requestApproval(ApprovalRequest request);

    /** 返回接受所有审批请求的处理器。 */
    static ApprovalHandler acceptAll() {
        return ignored -> CompletableFuture.completedFuture(ApprovalRequest.Decision.ACCEPT);
    }

    /** 返回拒绝所有审批请求的处理器。 */
    static ApprovalHandler declineAll() {
        return ignored -> CompletableFuture.completedFuture(ApprovalRequest.Decision.DECLINE);
    }
}
