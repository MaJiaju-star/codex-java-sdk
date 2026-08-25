package io.github.majiajustar.codex.exception;

/** 表示 app-server 无法启动，或传输读写失败。 */
public class CodexTransportException extends CodexException {
    /** 使用说明消息创建传输异常。 */
    public CodexTransportException(String message) {
        super(message);
    }

    /** 使用说明消息和底层原因创建传输异常。 */
    public CodexTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
