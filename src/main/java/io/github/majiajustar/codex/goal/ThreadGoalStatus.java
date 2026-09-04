package io.github.majiajustar.codex.goal;

/** Thread 长期目标的运行状态。 */
public enum ThreadGoalStatus {
    ACTIVE("active"),
    PAUSED("paused"),
    BLOCKED("blocked"),
    USAGE_LIMITED("usageLimited"),
    BUDGET_LIMITED("budgetLimited"),
    COMPLETE("complete"),
    UNKNOWN(null);

    private final String wireValue;

    ThreadGoalStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    /** 返回 app-server 使用的字符串值。 */
    public String wireValue() {
        return wireValue;
    }

    /** 将协议值转换为枚举，并对未来新增值保持兼容。 */
    public static ThreadGoalStatus fromWireValue(String value) {
        for (var status : values()) {
            if (status.wireValue != null && status.wireValue.equals(value)) return status;
        }
        return UNKNOWN;
    }
}
