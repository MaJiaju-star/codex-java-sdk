package io.github.majiajustar.codex.mcp;

/** MCP Server 初始化过程的状态。 */
public enum McpServerStartupState {
    STARTING("starting"),
    READY("ready"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    UNKNOWN(null);

    private final String wireValue;

    McpServerStartupState(String wireValue) {
        this.wireValue = wireValue;
    }

    /** 将协议值转换为枚举，并对未来新增值保持兼容。 */
    public static McpServerStartupState fromWireValue(String value) {
        for (var state : values()) {
            if (state.wireValue != null && state.wireValue.equals(value)) return state;
        }
        return UNKNOWN;
    }
}
