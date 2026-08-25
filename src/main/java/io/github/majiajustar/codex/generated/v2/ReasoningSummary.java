// 此文件由 scripts/generate_protocol_types.py 自动生成，请勿手工修改。
// 来源：v2/TurnStartParams.json#/definitions/ReasoningSummary
package io.github.majiajustar.codex.generated.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 模型推理摘要的输出模式，可用于调试和理解模型的推理过程。详情参见 https://platform.openai.com/docs/guides/reasoning?api-
 * mode=responses#reasoning-summaries
 */
public enum ReasoningSummary {
    AUTO("auto"),
    CONCISE("concise"),
    DETAILED("detailed"),
    NONE("none");

    private final String wireValue;

    ReasoningSummary(String wireValue) {
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
    public static ReasoningSummary fromWireValue(String value) {
        for (var candidate : ReasoningSummary.values()) {
            if (candidate.wireValue.equals(value)) return candidate;
        }
        throw new IllegalArgumentException("Unknown ReasoningSummary value: " + value);
    }
}
