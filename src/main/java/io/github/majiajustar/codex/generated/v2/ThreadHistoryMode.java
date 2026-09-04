// 此文件由 scripts/generate_protocol_types.py 自动生成，请勿手工修改。
// 来源：v2/ThreadListResponse.json#/definitions/ThreadHistoryMode
package io.github.majiajustar.codex.generated.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 会话历史的存储模式。
 */
public enum ThreadHistoryMode {
    LEGACY("legacy"),
    PAGINATED("paginated");

    private final String wireValue;

    ThreadHistoryMode(String wireValue) {
        this.wireValue = wireValue;
    }

    /** 返回 app-server 协议序列化时使用的准确值。 */
    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * 解析 app-server 协议值。
     *
     * @param value 准确的协议值
     * @return 匹配的枚举常量
     * @throws IllegalArgumentException 无法识别该值时抛出
     */
    @JsonCreator
    public static ThreadHistoryMode fromWireValue(String value) {
        for (var candidate : ThreadHistoryMode.values()) {
            if (candidate.wireValue.equals(value)) return candidate;
        }
        throw new IllegalArgumentException("Unknown ThreadHistoryMode value: " + value);
    }
}
