package io.github.majiajustar.codex.mcp;

/** HTTP MCP Server 在没有显式认证头时使用的认证流程。 */
public enum McpAuthMode {
    OAUTH("oauth"),
    CHATGPT("chatgpt");

    private final String wireValue;

    McpAuthMode(String wireValue) {
        this.wireValue = wireValue;
    }

    /** 返回 Codex 配置使用的值。 */
    public String wireValue() {
        return wireValue;
    }
}
