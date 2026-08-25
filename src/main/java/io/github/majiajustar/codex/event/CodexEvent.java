package io.github.majiajustar.codex.event;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Codex app-server 发出的通知。
 *
 * @param method JSON-RPC 通知方法名
 * @param params 保留前向兼容能力的原始通知参数
 */
public record CodexEvent(String method, JsonNode params) {
    /** 返回已知的事件分类；无法识别时返回 {@link CodexEventType#UNKNOWN}。 */
    public CodexEventType type() {
        return CodexEventType.fromMethod(method);
    }

    /** Return a typed notification while retaining unknown methods as raw payloads. */
    public CodexNotification notification() {
        return CodexNotification.from(this);
    }
}
