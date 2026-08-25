package io.github.majiajustar.codex.exception;

import com.fasterxml.jackson.databind.JsonNode;

/** 表示服务器已耗尽临时请求的内部重试次数。 */
public final class RetryLimitExceededException extends ServerBusyException {
    /** 创建上游重试次数耗尽错误响应。 */
    public RetryLimitExceededException(int code, String message, JsonNode data) {
        super(code, message, data);
    }
}
