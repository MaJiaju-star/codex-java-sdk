package io.github.majiajustar.codex.event;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.majiajustar.codex.generated.v2.ThreadStatus;
import io.github.majiajustar.codex.generated.v2.Turn;
import io.github.majiajustar.codex.internal.JsonSupport;
import io.github.majiajustar.codex.turn.TokenUsage;
import java.util.ArrayList;
import java.util.List;

/**
 * Typed view of the high-frequency app-server notifications used by interactive clients.
 *
 * <p>Unsupported methods are represented by {@link Unknown}; callers never lose access to the raw
 * parameters when the protocol adds a notification.
 */
public sealed interface CodexNotification
        permits CodexNotification.TurnStarted,
                CodexNotification.TurnCompleted,
                CodexNotification.ItemStarted,
                CodexNotification.ItemCompleted,
                CodexNotification.Delta,
                CodexNotification.FileChangePatchUpdated,
                CodexNotification.McpToolCallProgress,
                CodexNotification.TokenUsageUpdated,
                CodexNotification.ThreadStarted,
                CodexNotification.ThreadStatusChanged,
                CodexNotification.Error,
                CodexNotification.Unknown {
    /** Exact notification method. */
    String method();

    /** Original notification parameters. */
    JsonNode raw();

    /** Parse the strongest known notification type for an event. */
    static CodexNotification from(CodexEvent event) {
        var params = event.params();
        return switch (event.method()) {
            case "turn/started" -> new TurnStarted(
                    text(params, "threadId"), decode(params.path("turn"), Turn.class), params);
            case "turn/completed" -> new TurnCompleted(
                    text(params, "threadId"), decode(params.path("turn"), Turn.class), params);
            case "item/started" -> new ItemStarted(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    CodexItem.from(params.path("item")),
                    params.path("startedAtMs").asLong(),
                    params);
            case "item/completed" -> new ItemCompleted(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    CodexItem.from(params.path("item")),
                    params.path("completedAtMs").asLong(),
                    params);
            case "item/agentMessage/delta",
                    "item/plan/delta",
                    "item/reasoning/textDelta",
                    "item/reasoning/summaryTextDelta",
                    "item/commandExecution/outputDelta" -> new Delta(
                    event.method(),
                    text(params, "threadId"),
                    text(params, "turnId"),
                    text(params, "itemId"),
                    text(params, "delta"),
                    params);
            case "item/fileChange/patchUpdated" -> new FileChangePatchUpdated(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    text(params, "itemId"),
                    fileUpdates(params.path("changes")),
                    params);
            case "item/mcpToolCall/progress" -> new McpToolCallProgress(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    text(params, "itemId"),
                    text(params, "message"),
                    params);
            case "thread/tokenUsage/updated" -> new TokenUsageUpdated(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    TokenUsage.from(params.path("tokenUsage")),
                    params);
            case "thread/started" -> new ThreadStarted(
                    decode(params.path("thread"), io.github.majiajustar.codex.generated.v2.Thread.class),
                    params);
            case "thread/status/changed" -> new ThreadStatusChanged(
                    text(params, "threadId"),
                    decode(params.path("status"), ThreadStatus.class),
                    params);
            case "error" -> new Error(
                    text(params, "threadId"),
                    text(params, "turnId"),
                    params.path("willRetry").asBoolean(),
                    params.path("error"),
                    params);
            default -> new Unknown(event.method(), params);
        };
    }

    /** Turn entered the running state. */
    record TurnStarted(String threadId, Turn turn, JsonNode raw) implements CodexNotification {
        @Override
        public String method() {
            return "turn/started";
        }
    }

    /** Turn reached a terminal state. */
    record TurnCompleted(String threadId, Turn turn, JsonNode raw) implements CodexNotification {
        @Override
        public String method() {
            return "turn/completed";
        }
    }

    /** Item lifecycle started. */
    record ItemStarted(String threadId, String turnId, CodexItem item, long startedAtMs, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "item/started";
        }
    }

    /** Item lifecycle completed. */
    record ItemCompleted(
            String threadId, String turnId, CodexItem item, long completedAtMs, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "item/completed";
        }
    }

    /** Text or command-output delta associated with one item. */
    record Delta(
            String method,
            String threadId,
            String turnId,
            String itemId,
            String delta,
            JsonNode raw)
            implements CodexNotification {}

    /** Updated structured patch for an in-flight file-change item. */
    record FileChangePatchUpdated(
            String threadId,
            String turnId,
            String itemId,
            List<CodexItem.FileUpdate> changes,
            JsonNode raw)
            implements CodexNotification {
        public FileChangePatchUpdated {
            changes = List.copyOf(changes);
        }

        @Override
        public String method() {
            return "item/fileChange/patchUpdated";
        }
    }

    /** Human-readable progress from an MCP tool invocation. */
    record McpToolCallProgress(
            String threadId, String turnId, String itemId, String message, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "item/mcpToolCall/progress";
        }
    }

    /** Updated cumulative and last-turn token accounting. */
    record TokenUsageUpdated(String threadId, String turnId, TokenUsage usage, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "thread/tokenUsage/updated";
        }
    }

    /** New thread became available. */
    record ThreadStarted(io.github.majiajustar.codex.generated.v2.Thread thread, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "thread/started";
        }
    }

    /** Thread activity status changed. */
    record ThreadStatusChanged(String threadId, ThreadStatus status, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "thread/status/changed";
        }
    }

    /** Turn-scoped app-server error notification. */
    record Error(String threadId, String turnId, boolean willRetry, JsonNode error, JsonNode raw)
            implements CodexNotification {
        @Override
        public String method() {
            return "error";
        }
    }

    /** Notification method unknown to this SDK version. */
    record Unknown(String method, JsonNode raw) implements CodexNotification {}

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private static <T> T decode(JsonNode value, Class<T> type) {
        return JsonSupport.MAPPER.convertValue(value, type);
    }

    private static List<CodexItem.FileUpdate> fileUpdates(JsonNode values) {
        if (!values.isArray()) return List.of();
        var result = new ArrayList<CodexItem.FileUpdate>();
        values.forEach(value -> result.add(new CodexItem.FileUpdate(
                text(value, "path"), text(value, "kind"), text(value, "diff"))));
        return List.copyOf(result);
    }
}
