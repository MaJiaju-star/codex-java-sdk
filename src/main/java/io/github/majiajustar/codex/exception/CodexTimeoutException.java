package io.github.majiajustar.codex.exception;

/** 表示请求或回调超过配置的超时时间。 */
public final class CodexTimeoutException extends CodexException {
    /** 使用说明消息和底层等待失败原因创建超时异常。 */
    public CodexTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
