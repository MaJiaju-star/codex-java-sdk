package io.github.majiajustar.codex.exception;

/** Codex Java SDK 抛出的非受检异常基类。 */
public class CodexException extends RuntimeException {
    /** 使用说明消息创建 SDK 异常。 */
    public CodexException(String message) {
        super(message);
    }

    /** 使用说明消息和底层原因创建 SDK 异常。 */
    public CodexException(String message, Throwable cause) {
        super(message, cause);
    }
}
