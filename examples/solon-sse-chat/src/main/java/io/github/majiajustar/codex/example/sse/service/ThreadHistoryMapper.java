package io.github.majiajustar.codex.example.sse.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.majiajustar.codex.example.sse.model.ApiModels.HistoryItem;
import io.github.majiajustar.codex.example.sse.model.ApiModels.HistoryTurn;
import io.github.majiajustar.codex.example.sse.model.ApiModels.HistoryView;
import java.util.ArrayList;
import java.util.List;

/** Maps app-server thread history to a browser DTO with bounded command output. */
final class ThreadHistoryMapper {
    private static final int TOOL_OUTPUT_LIMIT = 100_000;

    private ThreadHistoryMapper() {}

    static HistoryView map(String sessionId, String threadId, JsonNode response) {
        ArrayList<HistoryTurn> turns = new ArrayList<>();
        for (JsonNode turn : response.path("thread").path("turns")) {
            ArrayList<HistoryItem> items = new ArrayList<>();
            turn.path("items").forEach(item -> items.add(mapItem(item)));
            turns.add(new HistoryTurn(
                    text(turn, "id"),
                    text(turn, "status"),
                    turn.path("error").path("message").isTextual()
                            ? turn.path("error").path("message").asText()
                            : null,
                    number(turn, "startedAt"),
                    number(turn, "completedAt"),
                    number(turn, "durationMs"),
                    List.copyOf(items)));
        }
        return new HistoryView(sessionId, threadId, List.copyOf(turns));
    }

    private static HistoryItem mapItem(JsonNode item) {
        String type = item.path("type").asText("unknown");
        String text = switch (type) {
            case "userMessage" -> userMessageText(item.path("content"));
            case "agentMessage", "plan" -> text(item, "text");
            case "reasoning" -> joinedText(item.path("summary"), item.path("content"));
            case "fileChange" -> "文件变更 " + item.path("changes").size() + " 项";
            case "mcpToolCall" -> "MCP " + text(item, "server") + "/" + text(item, "tool");
            case "webSearch" -> text(item, "query");
            default -> null;
        };
        String command = type.equals("commandExecution")
                ? ChatSession.redact(text(item, "command"))
                : null;
        String output = type.equals("commandExecution")
                ? ChatSession.redact(limit(text(item, "aggregatedOutput")))
                : null;
        return new HistoryItem(
                text(item, "id"),
                type,
                text(item, "phase"),
                text,
                command,
                text(item, "cwd"),
                text(item, "status"),
                output);
    }

    private static String userMessageText(JsonNode content) {
        ArrayList<String> parts = new ArrayList<>();
        for (JsonNode input : content) {
            String type = input.path("type").asText("unknown");
            parts.add(switch (type) {
                case "text" -> input.path("text").asText("");
                case "image" -> "[图片] " + input.path("url").asText("");
                case "localImage" -> "[本地图片] " + input.path("path").asText("");
                case "audio" -> "[音频] " + input.path("url").asText("");
                case "localAudio" -> "[本地音频] " + input.path("path").asText("");
                case "skill" -> "[Skill] " + input.path("name").asText("")
                        + " (" + input.path("path").asText("") + ")";
                case "mention" -> "@" + input.path("name").asText("")
                        + " (" + input.path("path").asText("") + ")";
                default -> "[" + type + "]";
            });
        }
        return String.join("\n", parts);
    }

    private static String joinedText(JsonNode first, JsonNode second) {
        ArrayList<String> parts = new ArrayList<>();
        first.forEach(value -> parts.add(value.asText()));
        second.forEach(value -> parts.add(value.asText()));
        return String.join("\n", parts);
    }

    private static String limit(String value) {
        if (value == null || value.length() <= TOOL_OUTPUT_LIMIT) return value;
        return value.substring(0, TOOL_OUTPUT_LIMIT) + "\n…[历史工具输出已截断]";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static Long number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.longValue() : null;
    }
}
