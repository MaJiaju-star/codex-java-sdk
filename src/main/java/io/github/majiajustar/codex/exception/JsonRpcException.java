package io.github.majiajustar.codex.exception;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * app-server 返回的 JSON-RPC 错误响应基类。
 *
 * <p>应用需要结构化错误处理时，可使用 {@link #code()}、{@link #rpcMessage()} 和
 * {@link #data()}。标准 JSON-RPC 错误会映射为更具体的异常子类。
 */
public class JsonRpcException extends CodexException {
    private final int code;
    private final String rpcMessage;
    private final JsonNode data;

    /**
     * 创建 JSON-RPC 异常。
     *
     * @param code JSON-RPC 数字错误码
     * @param message 服务器提供的错误消息
     * @param data 可选的结构化错误数据
     */
    public JsonRpcException(int code, String message, JsonNode data) {
        super("JSON-RPC error " + code + ": " + message);
        this.code = code;
        this.rpcMessage = message;
        this.data = data;
    }

    /** 返回 JSON-RPC 数字错误码。 */
    public int code() {
        return code;
    }

    /** 返回可选的结构化错误数据；不存在时返回 {@code null}。 */
    public JsonNode data() {
        return data;
    }

    /** 返回不带 SDK 前缀的服务器原始错误消息。 */
    public String rpcMessage() {
        return rpcMessage;
    }

    public static JsonRpcException map(int code, String message, JsonNode data) {
        return switch (code) {
            case -32700 -> new JsonRpcParseException(code, message, data);
            case -32600 -> new InvalidRequestException(code, message, data);
            case -32601 -> new MethodNotFoundException(code, message, data);
            case -32602 -> new InvalidParamsException(code, message, data);
            case -32603 -> new InternalRpcException(code, message, data);
            case -32001 -> retryLimitMessage(message)
                    ? new RetryLimitExceededException(code, message, data)
                    : new ServerBusyException(code, message, data);
            default -> {
                if (code >= -32099 && code <= -32000 && isOverloaded(data)) {
                    yield retryLimitMessage(message)
                            ? new RetryLimitExceededException(code, message, data)
                            : new ServerBusyException(code, message, data);
                }
                if (code >= -32099 && code <= -32000 && retryLimitMessage(message)) {
                    yield new RetryLimitExceededException(code, message, data);
                }
                yield new JsonRpcException(code, message, data);
            }
        };
    }

    private static boolean retryLimitMessage(String message) {
        var normalized = message.toLowerCase();
        return normalized.contains("retry limit") || normalized.contains("too many failed attempts");
    }

    private static boolean isOverloaded(JsonNode node) {
        if (node == null || node.isNull()) return false;
        if (node.isTextual()) {
            var value = node.asText();
            return value.equalsIgnoreCase("serverOverloaded")
                    || value.equalsIgnoreCase("server_overloaded");
        }
        if (node.isContainerNode()) {
            var children = node.elements();
            while (children.hasNext()) {
                if (isOverloaded(children.next())) return true;
            }
        }
        return false;
    }
}
