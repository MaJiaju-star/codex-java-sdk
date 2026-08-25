package io.github.majiajustar.codex.exception;

import com.fasterxml.jackson.databind.JsonNode;

/** JSON-RPC 参数无效错误（{@code -32602}）。 */
public final class InvalidParamsException extends JsonRpcException {
    /** 创建参数无效错误响应。 */
    public InvalidParamsException(int code, String message, JsonNode data) {
        super(code, message, data);
    }
}
