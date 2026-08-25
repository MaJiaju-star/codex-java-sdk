package io.github.majiajustar.codex.mcp;

/** MCP 工具的审批模式。 */
public enum McpToolApprovalMode {
    AUTO("auto"),
    PROMPT("prompt"),
    WRITES("writes"),
    APPROVE("approve");

    private final String wireValue;

    McpToolApprovalMode(String wireValue) {
        this.wireValue = wireValue;
    }

    /** 返回 Codex 配置使用的值。 */
    public String wireValue() {
        return wireValue;
    }
}
