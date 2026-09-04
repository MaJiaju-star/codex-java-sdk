package io.github.majiajustar.codex.mcp;

/** 模型可发现 MCP 工具的暴露位置。 */
public enum McpToolExposureSurface {
    CODE_MODE("code_mode"),
    DEFERRED("deferred"),
    DIRECT("direct");

    private final String wireValue;

    McpToolExposureSurface(String wireValue) {
        this.wireValue = wireValue;
    }

    /** 返回 Codex 配置使用的值。 */
    public String wireValue() {
        return wireValue;
    }
}
