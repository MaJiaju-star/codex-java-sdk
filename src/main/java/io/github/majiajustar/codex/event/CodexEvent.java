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

    /**
     * 返回强类型通知，同时为未知方法保留原始参数。
     *
     * @return 当前 SDK 能识别的最具体通知类型
     */
    public CodexNotification notification() {
        return CodexNotification.from(this);
    }
}
