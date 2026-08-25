package io.github.majiajustar.codex.exception;

import com.fasterxml.jackson.databind.JsonNode;

/** JSON-RPC 方法不存在错误（{@code -32601}）。 */
public final class MethodNotFoundException extends JsonRpcException {
    /** 创建方法不存在错误响应。 */
    public MethodNotFoundException(int code, String message, JsonNode data) {
        super(code, message, data);
    }
}
