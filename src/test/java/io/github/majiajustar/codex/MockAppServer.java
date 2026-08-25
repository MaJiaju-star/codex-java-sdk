package io.github.majiajustar.codex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public final class MockAppServer {
    private static final ObjectMapper JSON = new ObjectMapper();

    private MockAppServer() {}

    public static void main(String[] args) throws Exception {
        try (var in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                var out = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            var overloadAttempts = 0;
            String line;
            while ((line = in.readLine()) != null) {
                var message = JSON.readTree(line);
                if (!message.has("id")) continue;
                switch (message.path("method").asText()) {
                    case "initialize" -> reply(out, message, JSON.createObjectNode()
                            .put("userAgent", "mock-codex")
                            .put("platformFamily", "test"));
                    case "thread/start", "thread/resume", "thread/fork" -> {
                        var result = JSON.createObjectNode();
                        result.putObject("thread").put("id", "thread-1");
                        reply(out, message, result);
                    }
                    case "thread/list" -> {
                        var result = JSON.createObjectNode();
                        result.putArray("data").add(thread());
                        result.put("nextCursor", "next-page");
                        reply(out, message, result);
                    }
                    case "thread/archive" -> reply(out, message, JSON.createObjectNode());
                    case "thread/unarchive" -> {
                        var result = JSON.createObjectNode();
                        result.set("thread", thread());
                        reply(out, message, result);
                    }
                    case "model/list" -> {
                        var model = JSON.createObjectNode()
                                .put("id", "gpt-test")
                                .put("model", "gpt-test")
                                .put("displayName", "GPT Test")
                                .put("description", "Test model")
                                .put("hidden", false)
                                .put("isDefault", true)
                                .put("defaultReasoningEffort", "medium")
                                .put("supportsPersonality", true);
                        model.putArray("supportedReasoningEfforts")
                                .add(JSON.createObjectNode()
                                        .put("reasoningEffort", "medium")
                                        .put("description", "Balanced"));
                        model.putArray("inputModalities").add("text").add("image");
                        model.putArray("serviceTiers").add(JSON.createObjectNode()
                                .put("id", "priority")
                                .put("name", "Priority")
                                .put("description", "Fast queue"));
                        var result = JSON.createObjectNode();
                        result.putArray("data").add(model);
                        result.putNull("nextCursor");
                        reply(out, message, result);
                    }
                    case "test/overload" -> {
                        overloadAttempts++;
                        if (overloadAttempts < 3) {
                            error(out, message, -32001, "Server overloaded; retry later.", null);
                        } else {
                            reply(out, message, JSON.createObjectNode().put("attempts", overloadAttempts));
                        }
                    }
                    case "test/invalidParams" ->
                            error(out, message, -32602, "invalid test params", null);
                    case "turn/start" -> runTurn(in, out, message);
                    case "turn/steer" -> reply(out, message, JSON.createObjectNode().put("turnId", "turn-1"));
                    case "turn/interrupt" -> {
                        reply(out, message, JSON.createObjectNode());
                        notify(out, "turn/completed", JSON.createObjectNode()
                                .put("threadId", "thread-1")
                                .set("turn", turn("interrupted")));
                    }
                    case "thread/name/set", "thread/compact/start" ->
                            reply(out, message, JSON.createObjectNode());
                    default -> reply(out, message, JSON.createObjectNode());
                }
            }
        }
    }

    private static void runTurn(BufferedReader in, BufferedWriter out, JsonNode request) throws Exception {
        if (request.toString().contains("wait cancellation")) {
            notify(out, "turn/started", JSON.createObjectNode()
                    .put("threadId", "thread-1")
                    .set("turn", turn("inProgress")));
            var result = JSON.createObjectNode();
            result.set("turn", turn("inProgress"));
            reply(out, request, result);
            return;
        }
        write(out, JSON.createObjectNode()
                .put("id", "approval-1")
                .put("method", "item/commandExecution/requestApproval")
                .set("params", JSON.createObjectNode()
                        .put("threadId", "thread-1")
                        .put("turnId", "turn-1")
                        .put("itemId", "command-1")
                        .put("command", "echo test")
                        .put("cwd", "/workspace")));
        var approval = JSON.readTree(in.readLine());
        if (!approval.path("result").path("decision").asText().equals("accept")) {
            var error = JSON.createObjectNode().put("code", -32603).put("message", "approval rejected");
            var response = JSON.createObjectNode();
            response.set("id", request.get("id"));
            response.set("error", error);
            write(out, response);
            return;
        }

        notify(out, "turn/started", JSON.createObjectNode()
                .put("threadId", "thread-1")
                .set("turn", turn("inProgress")));
        var command = JSON.createObjectNode()
                .put("id", "command-1")
                .put("type", "commandExecution")
                .put("command", "echo test")
                .put("cwd", "/workspace")
                .put("status", "inProgress");
        notify(out, "item/started", JSON.createObjectNode()
                .put("threadId", "thread-1")
                .put("turnId", "turn-1")
                .put("startedAtMs", 10)
                .set("item", command));
        notify(out, "item/commandExecution/outputDelta", JSON.createObjectNode()
                .put("threadId", "thread-1")
                .put("turnId", "turn-1")
                .put("itemId", "command-1")
                .put("delta", "test\n"));
        command.put("status", request.toString().contains("failed tool") ? "failed" : "completed");
        notify(out, "item/completed", JSON.createObjectNode()
                .put("threadId", "thread-1")
                .put("turnId", "turn-1")
                .put("completedAtMs", 20)
                .set("item", command));
        var item = JSON.createObjectNode()
                .put("id", "item-1")
                .put("type", "agentMessage")
                .put("phase", "final_answer")
                .put("text", "Java SDK works");
        notify(out, "item/completed", JSON.createObjectNode()
                .put("threadId", "thread-1")
                .put("turnId", "turn-1")
                .put("completedAtMs", 30)
                .set("item", item));
        var totalUsage = JSON.createObjectNode()
                .put("totalTokens", 42)
                .put("inputTokens", 30)
                .put("cachedInputTokens", 5)
                .put("cacheWriteInputTokens", 2)
                .put("outputTokens", 12)
                .put("reasoningOutputTokens", 4);
        var lastUsage = totalUsage.deepCopy();
        var tokenUsage = JSON.createObjectNode();
        tokenUsage.set("total", totalUsage);
        tokenUsage.set("last", lastUsage);
        tokenUsage.put("modelContextWindow", 128000);
        notify(out, "thread/tokenUsage/updated", JSON.createObjectNode()
                .put("threadId", "thread-1")
                .put("turnId", "turn-1")
                .set("tokenUsage", tokenUsage));
        notify(out, "turn/completed", JSON.createObjectNode()
                .put("threadId", "thread-1")
                .set("turn", turn("completed")));

        var result = JSON.createObjectNode();
        result.set("turn", turn("inProgress"));
        reply(out, request, result);
    }

    private static ObjectNode turn(String status) {
        var turn = JSON.createObjectNode().put("id", "turn-1").put("status", status);
        turn.putArray("items");
        return turn;
    }

    private static ObjectNode thread() {
        var thread = JSON.createObjectNode()
                .put("id", "thread-1")
                .put("sessionId", "session-1")
                .put("modelProvider", "openai")
                .put("createdAt", 1)
                .put("updatedAt", 2)
                .put("recencyAt", 3)
                .put("status", "unused")
                .put("path", (String) null)
                .put("cwd", "/workspace")
                .put("cliVersion", "0.0.test")
                .put("source", "cli")
                .put("preview", "hello")
                .put("ephemeral", false);
        thread.set("status", JSON.createObjectNode().put("type", "idle"));
        thread.putArray("turns");
        return thread;
    }

    private static void notify(BufferedWriter out, String method, JsonNode params) throws Exception {
        write(out, JSON.createObjectNode().put("method", method).set("params", params));
    }

    private static void reply(BufferedWriter out, JsonNode request, JsonNode result) throws Exception {
        var response = JSON.createObjectNode();
        response.set("id", request.get("id"));
        response.set("result", result);
        write(out, response);
    }

    private static void error(
            BufferedWriter out,
            JsonNode request,
            int code,
            String message,
            JsonNode data)
            throws Exception {
        var error = JSON.createObjectNode().put("code", code).put("message", message);
        if (data != null) error.set("data", data);
        var response = JSON.createObjectNode();
        response.set("id", request.get("id"));
        response.set("error", error);
        write(out, response);
    }

    private static void write(BufferedWriter out, JsonNode message) throws Exception {
        out.write(JSON.writeValueAsString(message));
        out.newLine();
        out.flush();
    }
}
