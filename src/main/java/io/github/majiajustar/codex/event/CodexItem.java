package io.github.majiajustar.codex.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Strongly typed view of an app-server {@code ThreadItem}.
 *
 * <p>The most common item variants expose typed fields. New or less common variants are represented
 * by {@link Unknown}, which preserves the original payload for forward compatibility.
 */
public sealed interface CodexItem
        permits CodexItem.UserMessage,
                CodexItem.HookPrompt,
                CodexItem.AgentMessage,
                CodexItem.Plan,
                CodexItem.Reasoning,
                CodexItem.CommandExecution,
                CodexItem.FileChange,
                CodexItem.McpToolCall,
                CodexItem.DynamicToolCall,
                CodexItem.CollabAgentToolCall,
                CodexItem.SubAgentActivity,
                CodexItem.WebSearch,
                CodexItem.ImageView,
                CodexItem.Sleep,
                CodexItem.ImageGeneration,
                CodexItem.EnteredReviewMode,
                CodexItem.ExitedReviewMode,
                CodexItem.ContextCompaction,
                CodexItem.Unknown {
    /** Item identifier assigned by Codex. */
    String id();

    /** Exact app-server discriminator. */
    String type();

    /** Original payload, including fields added by newer app-server versions. */
    JsonNode raw();

    /** Parse one app-server item without discarding unknown variants or fields. */
    static CodexItem from(JsonNode item) {
        var type = text(item, "type");
        var id = text(item, "id");
        return switch (type) {
            case "userMessage" -> new UserMessage(id, jsonList(item.path("content")), item);
            case "hookPrompt" -> new HookPrompt(id, jsonList(item.path("fragments")), item);
            case "agentMessage" -> new AgentMessage(
                    id,
                    text(item, "text"),
                    MessagePhase.fromWireValue(nullableText(item, "phase")),
                    item);
            case "plan" -> new Plan(id, text(item, "text"), item);
            case "reasoning" -> new Reasoning(
                    id,
                    stringList(item.path("summary")),
                    stringList(item.path("content")),
                    item);
            case "commandExecution" -> new CommandExecution(
                    id,
                    nullableText(item, "pluginId"),
                    nullableText(item, "scriptPath"),
                    text(item, "command"),
                    text(item, "cwd"),
                    nullableText(item, "processId"),
                    text(item, "source"),
                    text(item, "status"),
                    jsonList(item.path("commandActions")),
                    nullableText(item, "aggregatedOutput"),
                    nullableInteger(item, "exitCode"),
                    nullableLong(item, "durationMs"),
                    item);
            case "fileChange" -> new FileChange(
                    id, fileChanges(item.path("changes")), text(item, "status"), item);
            case "mcpToolCall" -> new McpToolCall(
                    id,
                    text(item, "server"),
                    text(item, "tool"),
                    text(item, "status"),
                    item.get("arguments"),
                    nullableText(item, "pluginId"),
                    nullableBoolean(item, "readOnlyHint"),
                    item.get("result"),
                    item.get("error"),
                    nullableLong(item, "durationMs"),
                    item);
            case "dynamicToolCall" -> new DynamicToolCall(
                    id,
                    nullableText(item, "namespace"),
                    text(item, "tool"),
                    text(item, "status"),
                    item.get("arguments"),
                    jsonList(item.path("contentItems")),
                    nullableBoolean(item, "success"),
                    nullableLong(item, "durationMs"),
                    item);
            case "collabAgentToolCall" -> new CollabAgentToolCall(
                    id,
                    text(item, "senderThreadId"),
                    stringList(item.path("receiverThreadIds")),
                    text(item, "tool"),
                    text(item, "status"),
                    nullableText(item, "prompt"),
                    nullableText(item, "model"),
                    nullableText(item, "reasoningEffort"),
                    item.get("agentsStates"),
                    item);
            case "subAgentActivity" -> new SubAgentActivity(
                    id,
                    text(item, "agentThreadId"),
                    text(item, "agentPath"),
                    text(item, "kind"),
                    item);
            case "webSearch" -> new WebSearch(
                    id,
                    nullableText(item, "query"),
                    item.get("action"),
                    item.get("results"),
                    item);
            case "imageView" -> new ImageView(id, text(item, "path"), item);
            case "sleep" -> new Sleep(id, item.path("durationMs").asLong(), item);
            case "imageGeneration" -> new ImageGeneration(
                    id,
                    text(item, "status"),
                    text(item, "result"),
                    nullableText(item, "revisedPrompt"),
                    nullableText(item, "savedPath"),
                    nullableBoolean(item, "transparentBackground"),
                    item.get("failure"),
                    item);
            case "enteredReviewMode" ->
                new EnteredReviewMode(id, text(item, "review"), item);
            case "exitedReviewMode" -> new ExitedReviewMode(id, text(item, "review"), item);
            case "contextCompaction" -> new ContextCompaction(id, item);
            default -> new Unknown(id, type, item);
        };
    }

    /** Prompt fragments produced by a hook. */
    record HookPrompt(String id, List<JsonNode> fragments, JsonNode raw) implements CodexItem {
        public HookPrompt {
            fragments = List.copyOf(fragments);
        }

        @Override
        public String type() {
            return "hookPrompt";
        }
    }

    /** Parse a list of raw item payloads. */
    static List<CodexItem> fromAll(List<JsonNode> items) {
        return items.stream().map(CodexItem::from).toList();
    }

    /** User input persisted as a turn item. */
    record UserMessage(String id, List<JsonNode> content, JsonNode raw) implements CodexItem {
        public UserMessage {
            content = List.copyOf(content);
        }

        @Override
        public String type() {
            return "userMessage";
        }
    }

    /** Assistant text, including whether it is commentary or the terminal answer. */
    record AgentMessage(String id, String text, MessagePhase phase, JsonNode raw)
            implements CodexItem {
        @Override
        public String type() {
            return "agentMessage";
        }
    }

    /** Proposed or completed plan text. */
    record Plan(String id, String text, JsonNode raw) implements CodexItem {
        @Override
        public String type() {
            return "plan";
        }
    }

    /** Model reasoning summary and content fragments. */
    record Reasoning(String id, List<String> summary, List<String> content, JsonNode raw)
            implements CodexItem {
        public Reasoning {
            summary = List.copyOf(summary);
            content = List.copyOf(content);
        }

        @Override
        public String type() {
            return "reasoning";
        }
    }

    /** Shell command lifecycle and result. */
    record CommandExecution(
            String id,
            String pluginId,
            String scriptPath,
            String command,
            String cwd,
            String processId,
            String source,
            String status,
            List<JsonNode> commandActions,
            String aggregatedOutput,
            Integer exitCode,
            Long durationMs,
            JsonNode raw)
            implements CodexItem {
        public CommandExecution {
            commandActions = List.copyOf(commandActions);
        }

        @Override
        public String type() {
            return "commandExecution";
        }
    }

    /** One file update included in a file-change item. */
    record FileUpdate(String path, String kind, String diff) {}

    /** File patch lifecycle and changes. */
    record FileChange(String id, List<FileUpdate> changes, String status, JsonNode raw)
            implements CodexItem {
        public FileChange {
            changes = List.copyOf(changes);
        }

        @Override
        public String type() {
            return "fileChange";
        }
    }

    /** MCP tool invocation and its structured or raw result. */
    record McpToolCall(
            String id,
            String server,
            String tool,
            String status,
            JsonNode arguments,
            String pluginId,
            Boolean readOnlyHint,
            JsonNode result,
            JsonNode error,
            Long durationMs,
            JsonNode raw)
            implements CodexItem {
        @Override
        public String type() {
            return "mcpToolCall";
        }
    }

    /** Client-provided dynamic tool invocation. */
    record DynamicToolCall(
            String id,
            String namespace,
            String tool,
            String status,
            JsonNode arguments,
            List<JsonNode> contentItems,
            Boolean success,
            Long durationMs,
            JsonNode raw)
            implements CodexItem {
        public DynamicToolCall {
            contentItems = List.copyOf(contentItems);
        }

        @Override
        public String type() {
            return "dynamicToolCall";
        }
    }

    /** Coordination call between a parent agent and one or more sub-agents. */
    record CollabAgentToolCall(
            String id,
            String senderThreadId,
            List<String> receiverThreadIds,
            String tool,
            String status,
            String prompt,
            String model,
            String reasoningEffort,
            JsonNode agentsStates,
            JsonNode raw)
            implements CodexItem {
        public CollabAgentToolCall {
            receiverThreadIds = List.copyOf(receiverThreadIds);
        }

        @Override
        public String type() {
            return "collabAgentToolCall";
        }
    }

    /** Lifecycle marker emitted by a sub-agent. */
    record SubAgentActivity(
            String id, String agentThreadId, String agentPath, String kind, JsonNode raw)
            implements CodexItem {
        @Override
        public String type() {
            return "subAgentActivity";
        }
    }

    /** Web search request and result payload. */
    record WebSearch(String id, String query, JsonNode action, JsonNode results, JsonNode raw)
            implements CodexItem {
        @Override
        public String type() {
            return "webSearch";
        }
    }

    /** Local image inspected by the agent. */
    record ImageView(String id, String path, JsonNode raw) implements CodexItem {
        @Override
        public String type() {
            return "imageView";
        }
    }

    /** Deliberate agent sleep interval. */
    record Sleep(String id, long durationMs, JsonNode raw) implements CodexItem {
        @Override
        public String type() {
            return "sleep";
        }
    }

    /** Image generation lifecycle and output. */
    record ImageGeneration(
            String id,
            String status,
            String result,
            String revisedPrompt,
            String savedPath,
            Boolean transparentBackground,
            JsonNode failure,
            JsonNode raw)
            implements CodexItem {
        @Override
        public String type() {
            return "imageGeneration";
        }
    }

    /** Agent entered review mode. */
    record EnteredReviewMode(String id, String review, JsonNode raw) implements CodexItem {
        @Override
        public String type() {
            return "enteredReviewMode";
        }
    }

    /** Agent exited review mode. */
    record ExitedReviewMode(String id, String review, JsonNode raw) implements CodexItem {
        @Override
        public String type() {
            return "exitedReviewMode";
        }
    }

    /** Thread context was compacted. */
    record ContextCompaction(String id, JsonNode raw) implements CodexItem {
        @Override
        public String type() {
            return "contextCompaction";
        }
    }

    /** Unknown or intentionally unexpanded item variant. */
    record Unknown(String id, String type, JsonNode raw) implements CodexItem {}

    /** Assistant message phase from the app-server protocol. */
    enum MessagePhase {
        COMMENTARY,
        FINAL_ANSWER,
        UNSPECIFIED,
        UNKNOWN;

        static MessagePhase fromWireValue(String value) {
            if (value == null) return UNSPECIFIED;
            return switch (value) {
                case "commentary" -> COMMENTARY;
                case "final_answer" -> FINAL_ANSWER;
                default -> UNKNOWN;
            };
        }
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private static String nullableText(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Integer nullableInteger(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.intValue();
    }

    private static Long nullableLong(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.longValue();
    }

    private static Boolean nullableBoolean(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.booleanValue();
    }

    private static List<String> stringList(JsonNode values) {
        if (!values.isArray()) return List.of();
        var result = new ArrayList<String>();
        values.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private static List<JsonNode> jsonList(JsonNode values) {
        if (!values.isArray()) return List.of();
        var result = new ArrayList<JsonNode>();
        values.forEach(result::add);
        return List.copyOf(result);
    }

    private static List<FileUpdate> fileChanges(JsonNode values) {
        if (!values.isArray()) return List.of();
        var result = new ArrayList<FileUpdate>();
        values.forEach(value -> result.add(new FileUpdate(
                text(value, "path"), text(value, "kind"), text(value, "diff"))));
        return List.copyOf(result);
    }
}
