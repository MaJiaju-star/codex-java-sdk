package io.github.majiajustar.codex.exception;

import com.fasterxml.jackson.databind.JsonNode;

/** JSON-RPC 无效请求错误（{@code -32600}）。 */
public final class InvalidRequestException extends JsonRpcException {
    /** 创建无效请求错误响应。 */
    public InvalidRequestException(int code, String message, JsonNode data) {
        super(code, message, data);
    }
}
