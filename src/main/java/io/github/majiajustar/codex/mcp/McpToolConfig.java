package io.github.majiajustar.codex.mcp;

import java.util.Objects;

/** 单个 MCP 工具的配置。 */
public record McpToolConfig(McpToolApprovalMode approvalMode) {
    public McpToolConfig {
        Objects.requireNonNull(approvalMode, "approvalMode");
    }

    /** 创建只包含审批模式的工具配置。 */
    public static McpToolConfig approval(McpToolApprovalMode approvalMode) {
        return new McpToolConfig(approvalMode);
    }
}
