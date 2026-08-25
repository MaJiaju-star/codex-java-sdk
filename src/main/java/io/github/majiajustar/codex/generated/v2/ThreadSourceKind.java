// 此文件由 scripts/generate_protocol_types.py 自动生成，请勿手工修改。
// 来源：v2/ThreadListParams.json#/definitions/ThreadSourceKind
package io.github.majiajustar.codex.generated.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 会话的来源类别。
 */
public enum ThreadSourceKind {
    CLI("cli"),
    VSCODE("vscode"),
    EXEC("exec"),
    APP_SERVER("appServer"),
    SUB_AGENT("subAgent"),
    SUB_AGENT_REVIEW("subAgentReview"),
    SUB_AGENT_COMPACT("subAgentCompact"),
    SUB_AGENT_THREAD_SPAWN("subAgentThreadSpawn"),
    SUB_AGENT_OTHER("subAgentOther"),
    UNKNOWN("unknown");

    private final String wireValue;

    ThreadSourceKind(String wireValue) {
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
    public static ThreadSourceKind fromWireValue(String value) {
        for (var candidate : ThreadSourceKind.values()) {
            if (candidate.wireValue.equals(value)) return candidate;
        }
        throw new IllegalArgumentException("Unknown ThreadSourceKind value: " + value);
    }
}
