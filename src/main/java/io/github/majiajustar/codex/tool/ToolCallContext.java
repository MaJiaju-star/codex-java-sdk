package io.github.majiajustar.codex.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 在审批请求和生命周期通知之间统一标识一次工具调用。
 *
 * @param threadId app-server 提供的所属会话 ID
 * @param turnId app-server 提供的所属轮次 ID
 * @param itemId app-server 提供的工具项 ID
 * @param kind 标准化后的工具类别
 * @param toolName 协议中的工具名称
 * @param command 命令执行时的命令文本；其他情况为 {@code null}
 * @param workingDirectory 命令执行时的工作目录；其他情况为 {@code null}
 * @param raw 原始协议项或请求参数
 */
public record ToolCallContext(
        String threadId,
        String turnId,
        String itemId,
        Kind kind,
        String toolName,
        String command,
        String workingDirectory,
        JsonNode raw) {

    /** SDK 可以识别的工具大类。 */
    public enum Kind {
        COMMAND,
        FILE_CHANGE,
        MCP,
        WEB_SEARCH,
        UNKNOWN
    }

    static ToolCallContext fromApproval(String method, JsonNode params) {
        var kind = method.equals("item/commandExecution/requestApproval") ? Kind.COMMAND : Kind.FILE_CHANGE;
        return new ToolCallContext(
                text(params, "threadId"),
                text(params, "turnId"),
                text(params, "itemId"),
                kind,
                kind == Kind.COMMAND ? "commandExecution" : "fileChange",
                text(params, "command"),
                text(params, "cwd"),
                params);
    }

    public static ToolCallContext fromItem(JsonNode params) {
        var item = params.path("item");
        var type = item.path("type").asText();
        var kind = switch (type) {
            case "commandExecution" -> Kind.COMMAND;
            case "fileChange" -> Kind.FILE_CHANGE;
            case "mcpToolCall" -> Kind.MCP;
            case "webSearch" -> Kind.WEB_SEARCH;
            default -> Kind.UNKNOWN;
        };
        var toolName = kind == Kind.MCP ? item.path("tool").asText(type) : type;
        return new ToolCallContext(
                text(params, "threadId"),
                text(params, "turnId"),
                item.path("id").asText(null),
                kind,
                toolName,
                item.path("command").asText(null),
                item.path("cwd").asText(null),
                item);
    }

    private static String text(JsonNode node, String field) {
        var value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }
}
