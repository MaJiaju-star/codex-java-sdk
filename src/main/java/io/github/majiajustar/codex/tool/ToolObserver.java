package io.github.majiajustar.codex.tool;

/**
 * 接收非阻塞的工具生命周期回调，但不参与授权控制。
 *
 * <p>观察器失败会与传输及工具执行隔离。回调会在 SDK 的工具回调执行器上串行执行。
 */
public interface ToolObserver {
    /** 收到审批请求时调用。 */
    default void onApprovalRequested(ApprovalRequest request) {}

    /** 已识别的工具项开始时调用。 */
    default void onStarted(ToolCallContext context) {}

    /** 收到增量命令输出片段时调用。 */
    default void onOutput(ToolCallContext context, String delta) {}

    /** 已识别的工具项完成后调用。 */
    default void onCompleted(ToolCallResult result) {}
}
