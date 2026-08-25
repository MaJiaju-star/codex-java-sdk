package io.github.majiajustar.codex.exception;

import com.fasterxml.jackson.databind.JsonNode;

/** JSON-RPC 内部错误（{@code -32603}）。 */
public final class InternalRpcException extends JsonRpcException {
    /** 创建内部错误响应。 */
    public InternalRpcException(int code, String message, JsonNode data) {
        super(code, message, data);
    }
}
