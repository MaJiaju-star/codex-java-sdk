package io.github.majiajustar.codex.exception;

import com.fasterxml.jackson.databind.JsonNode;

/** 可以安全重试的临时 app-server 过载错误。 */
public class ServerBusyException extends JsonRpcException {
    /** 创建可重试的服务器过载错误响应。 */
    public ServerBusyException(int code, String message, JsonNode data) {
        super(code, message, data);
    }
}
