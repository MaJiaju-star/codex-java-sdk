package io.github.majiajustar.codex.exception;

import com.fasterxml.jackson.databind.JsonNode;

/** JSON-RPC 解析错误（{@code -32700}）。 */
public final class JsonRpcParseException extends JsonRpcException {
    /** 创建解析错误响应。 */
    public JsonRpcParseException(int code, String message, JsonNode data) {
        super(code, message, data);
    }
}
