package io.github.majiajustar.codex.mcp;

import io.github.majiajustar.codex.CodexClient;
import java.util.Objects;

/** 通过 app-server 管理和直接调用 MCP Server 的强类型客户端。 */
public final class McpClient {
    private final CodexClient codex;

    public McpClient(CodexClient codex) {
        this.codex = Objects.requireNonNull(codex, "codex");
    }

    /** 使用默认分页参数读取 MCP Server 状态。 */
    public McpServerStatusPage listStatuses() {
        return listStatuses(McpStatusListOptions.defaults());
    }

    /** 分页读取 MCP Server 的连接、工具、资源和认证状态。 */
    public McpServerStatusPage listStatuses(McpStatusListOptions options) {
        var result = codex.request("mcpServerStatus/list", options.toJson());
        return McpServerStatusPage.from(result);
    }

    /** 根据当前配置重新加载全部 MCP Server。 */
    public void reload() {
        codex.request("config/mcpServer/reload", null);
    }

    /** 启动 OAuth 登录并返回需要在浏览器中打开的授权地址。 */
    public McpOAuthLoginResult startOAuthLogin(McpOAuthLoginRequest request) {
        var result = codex.request("mcpServer/oauth/login", request.toJson());
        return McpOAuthLoginResult.from(result);
    }

    /** 读取 MCP Server 暴露的资源。 */
    public McpResourceReadResult readResource(McpResourceReadRequest request) {
        return McpResourceReadResult.from(
                codex.request("mcpServer/resource/read", request.toJson()));
    }

    /** 在指定线程上下文中直接调用 MCP 工具。 */
    public McpToolCallResponse callTool(McpToolCallRequest request) {
        return McpToolCallResponse.from(codex.request("mcpServer/tool/call", request.toJson()));
    }
}
