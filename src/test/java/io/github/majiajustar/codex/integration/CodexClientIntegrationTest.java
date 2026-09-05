package io.github.majiajustar.codex.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.majiajustar.codex.CancellationToken;
import io.github.majiajustar.codex.CodexClient;
import io.github.majiajustar.codex.CodexClientConfig;
import io.github.majiajustar.codex.MockAppServer;
import io.github.majiajustar.codex.RetryPolicy;
import io.github.majiajustar.codex.event.CodexEvent;
import io.github.majiajustar.codex.event.CodexEventType;
import io.github.majiajustar.codex.event.CodexItem;
import io.github.majiajustar.codex.event.CodexNotification;
import io.github.majiajustar.codex.exception.InvalidParamsException;
import io.github.majiajustar.codex.exception.JsonRpcException;
import io.github.majiajustar.codex.internal.JsonSupport;
import io.github.majiajustar.codex.sandbox.SandboxPolicy;
import io.github.majiajustar.codex.thread.ThreadListOptions;
import io.github.majiajustar.codex.thread.ThreadOptions;
import io.github.majiajustar.codex.tool.ApprovalHandler;
import io.github.majiajustar.codex.tool.ApprovalRequest;
import io.github.majiajustar.codex.tool.ToolCallContext;
import io.github.majiajustar.codex.tool.ToolCallResult;
import io.github.majiajustar.codex.tool.ToolInterceptor;
import io.github.majiajustar.codex.tool.ToolObserver;
import io.github.majiajustar.codex.turn.TurnOptions;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import io.github.majiajustar.codex.generated.v2.Personality;
import io.github.majiajustar.codex.generated.v2.ReasoningSummary;
import io.github.majiajustar.codex.generated.v2.SortDirection;
import io.github.majiajustar.codex.generated.v2.ThreadArchiveResponse;
import io.github.majiajustar.codex.generated.v2.ThreadListResponse;
import io.github.majiajustar.codex.generated.v2.ThreadSortKey;
import io.github.majiajustar.codex.generated.v2.ThreadStatus;
import io.github.majiajustar.codex.generated.v2.ThreadStatusType;
import io.github.majiajustar.codex.generated.v2.ThreadUnarchiveResponse;
import io.github.majiajustar.codex.generated.v2.TurnItemsView;
import io.github.majiajustar.codex.generated.v2.TurnStatus;
import org.junit.jupiter.api.Test;

class CodexClientIntegrationTest {
    @Test
    void runsTurnAndReplaysEventsThatArriveBeforeTurnResponse() throws Exception {
        var handlerUsedVirtualThread = new AtomicBoolean();
        var config = CodexClientConfig.builder()
                .command(mockServerCommand())
                .requestTimeout(Duration.ofSeconds(10))
                .serverRequestHandler((method, params) -> {
                    handlerUsedVirtualThread.set(Thread.currentThread().isVirtual());
                    return JsonSupport.MAPPER.createObjectNode().put("decision", "accept");
                })
                .build();

        try (var codex = CodexClient.create(config)) {
            assertEquals("mock-codex", codex.metadata().path("userAgent").asText());
            var turn = codex.startThread().startTurn("test prompt");
            var received = new CopyOnWriteArrayList<CodexEvent>();
            var completed = new CountDownLatch(1);
            turn.events().subscribe(new Flow.Subscriber<>() {
                public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                public void onNext(CodexEvent event) { received.add(event); }
                public void onError(Throwable error) { completed.countDown(); }
                public void onComplete() { completed.countDown(); }
            });

            var result = turn.resultAsync().get(10, TimeUnit.SECONDS);
            assertTrue(completed.await(10, TimeUnit.SECONDS));
            assertEquals("completed", result.status());
            assertEquals("Java SDK works", result.finalResponse());
            assertEquals(42, result.typedUsage().total().totalTokens());
            assertEquals(128000, result.typedUsage().modelContextWindow());
            assertEquals(2, result.typedItems().size());
            var finalItem = (CodexItem.AgentMessage) result.typedItems().get(1);
            assertEquals(CodexItem.MessagePhase.FINAL_ANSWER, finalItem.phase());
            assertEquals(List.of(
                            "turn/started",
                            "item/started",
                            "item/commandExecution/outputDelta",
                            "item/completed",
                            "item/completed",
                            "thread/tokenUsage/updated",
                            "turn/completed"),
                    received.stream().map(CodexEvent::method).toList());
            assertEquals(List.of(
                            CodexEventType.TURN_STARTED,
                            CodexEventType.ITEM_STARTED,
                            CodexEventType.COMMAND_OUTPUT_DELTA,
                            CodexEventType.ITEM_COMPLETED,
                            CodexEventType.ITEM_COMPLETED,
                            CodexEventType.TOKEN_USAGE_UPDATED,
                            CodexEventType.TURN_COMPLETED),
                    received.stream().map(CodexEvent::type).toList());
            var completedItem = (CodexNotification.ItemCompleted)
                    received.get(4).notification();
            assertEquals("Java SDK works", ((CodexItem.AgentMessage) completedItem.item()).text());
            assertTrue(handlerUsedVirtualThread.get());
        }
    }

    @Test
    void invokesTypedToolInterceptorsAndObserversInLifecycleOrder() throws Exception {
        var callbacks = new CopyOnWriteArrayList<String>();
        var afterCalled = new CountDownLatch(1);
        var config = CodexClientConfig.builder()
                .command(mockServerCommand())
                .requestTimeout(Duration.ofSeconds(10))
                .approvalHandler(request -> {
                    callbacks.add("handler:" + request.context().command());
                    return CompletableFuture.completedFuture(ApprovalRequest.Decision.ACCEPT);
                })
                .toolInterceptor(new ToolInterceptor() {
                    @Override
                    public java.util.concurrent.CompletionStage<BeforeResult> beforeToolCall(
                            ApprovalRequest request) {
                        callbacks.add("before:" + request.context().kind());
                        return CompletableFuture.completedFuture(new BeforeResult.Continue());
                    }

                    @Override
                    public java.util.concurrent.CompletionStage<Void> afterToolCall(ToolCallResult result) {
                        callbacks.add("after:" + result.successful());
                        afterCalled.countDown();
                        return CompletableFuture.completedFuture(null);
                    }
                })
                .toolObserver(new ToolObserver() {
                    @Override
                    public void onApprovalRequested(ApprovalRequest request) {
                        callbacks.add("approval:" + request.context().itemId());
                    }

                    @Override
                    public void onStarted(ToolCallContext context) {
                        callbacks.add("started:" + context.command());
                    }

                    @Override
                    public void onOutput(ToolCallContext context, String delta) {
                        callbacks.add("output:" + delta.strip());
                    }

                    @Override
                    public void onCompleted(ToolCallResult result) {
                        callbacks.add("completed:" + result.context().itemId());
                    }
                })
                .build();

        try (var codex = CodexClient.create(config)) {
            var result = codex.startThread().startTurn("test prompt").resultAsync().get(10, TimeUnit.SECONDS);
            assertEquals("completed", result.status());
            assertTrue(afterCalled.await(10, TimeUnit.SECONDS));
            assertEquals(List.of(
                            "approval:command-1",
                            "before:COMMAND",
                            "handler:echo test",
                            "started:echo test",
                            "output:test",
                            "completed:command-1",
                            "after:true"),
                    callbacks);
        }
    }

    @Test
    void toolInterceptorCanDeclineBeforeApprovalHandler() {
        var handlerCalled = new AtomicBoolean();
        var config = CodexClientConfig.builder()
                .command(mockServerCommand())
                .requestTimeout(Duration.ofSeconds(10))
                .approvalHandler(request -> {
                    handlerCalled.set(true);
                    return CompletableFuture.completedFuture(ApprovalRequest.Decision.ACCEPT);
                })
                .toolInterceptor(new ToolInterceptor() {
                    @Override
                    public java.util.concurrent.CompletionStage<BeforeResult> beforeToolCall(
                            ApprovalRequest request) {
                        return CompletableFuture.completedFuture(
                                new BeforeResult.Decide(ApprovalRequest.Decision.DECLINE));
                    }
                })
                .build();

        try (var codex = CodexClient.create(config)) {
            assertThrows(JsonRpcException.class, () -> codex.startThread().startTurn("test prompt"));
            assertFalse(handlerCalled.get());
        }
    }

    @Test
    void reportsFailedToolStatusWithoutAnErrorPayload() throws Exception {
        var completed = new AtomicReference<ToolCompletion>();
        var completionObserved = new CountDownLatch(1);
        var config = CodexClientConfig.builder()
                .command(mockServerCommand())
                .requestTimeout(Duration.ofSeconds(10))
                .approvalHandler(ApprovalHandler.acceptAll())
                .toolObserver(new ToolObserver() {
                    @Override
                    public void onCompleted(ToolCallResult result) {
                        completed.set(new ToolCompletion(
                                result.successful(), result.error(), result.item().path("status").asText()));
                        completionObserved.countDown();
                    }
                })
                .build();

        try (var codex = CodexClient.create(config)) {
            codex.startThread().startTurn("failed tool").resultAsync().get(10, TimeUnit.SECONDS);
            assertTrue(completionObserved.await(10, TimeUnit.SECONDS));
            assertEquals(new ToolCompletion(false, null, "failed"), completed.get());
        }
    }

    @Test
    void listsArchivesAndUnarchivesThreadsWithGeneratedTypes() {
        var config = CodexClientConfig.builder()
                .command(mockServerCommand())
                .requestTimeout(Duration.ofSeconds(10))
                .build();

        try (var codex = CodexClient.create(config)) {
            var options = ThreadListOptions.builder()
                    .archived(false)
                    .limit(20)
                    .sortKey(ThreadSortKey.RECENCY_AT)
                    .sortDirection(SortDirection.DESC)
                    .build();
            var thread = new io.github.majiajustar.codex.generated.v2.Thread(
                    null,
                    null,
                    "0.0.test",
                    1,
                    "/workspace",
                    false,
                    null,
                    null,
                    null,
                    "thread-1",
                    null,
                    "openai",
                    null,
                    null,
                    null,
                    "hello",
                    null,
                    null,
                    3L,
                    null,
                    null,
                    "session-1",
                    JsonSupport.MAPPER.getNodeFactory().textNode("cli"),
                    new ThreadStatus(ThreadStatusType.IDLE, null),
                    null,
                    List.of(),
                    2);
            var expectedPage = new ThreadListResponse(null, List.of(thread), "next-page");

            assertEquals(expectedPage, codex.listThreads(options));
            assertEquals(new ThreadArchiveResponse(), codex.archiveThread("thread-1"));
            assertEquals(new ThreadUnarchiveResponse(thread), codex.unarchiveThread("thread-1"));
        }
    }

    @Test
    void readsStronglyTypedThreadHistory() {
        var config = CodexClientConfig.builder()
                .command(mockServerCommand())
                .requestTimeout(Duration.ofSeconds(10))
                .build();

        try (var codex = CodexClient.create(config)) {
            var history = codex.resumeThread("thread-1").readHistory();

            assertEquals("thread-1", history.id());
            assertEquals("SDK history", history.name());
            assertEquals("gpt-test", history.model());
            assertEquals(1, history.turns().size());
            var turn = history.turns().getFirst();
            assertEquals("turn-history-1", turn.id());
            assertEquals(TurnStatus.COMPLETED, turn.status());
            assertEquals(TurnItemsView.FULL, turn.itemsView());
            assertEquals(2, turn.items().size());
            assertEquals("检查项目", ((CodexItem.UserMessage) turn.items().getFirst())
                    .content()
                    .getFirst()
                    .path("text")
                    .asText());
            assertEquals("检查完成", ((CodexItem.AgentMessage) turn.items().getLast()).text());
            assertEquals("thread-1", history.raw().path("thread").path("id").asText());
        }
    }

    @Test
    void returnsStronglyTypedModelCatalog() {
        var config = CodexClientConfig.builder()
                .command(mockServerCommand())
                .requestTimeout(Duration.ofSeconds(10))
                .build();

        try (var codex = CodexClient.create(config)) {
            var catalog = codex.listModels();
            assertEquals(1, catalog.data().size());
            var model = catalog.data().getFirst();
            assertEquals("gpt-test", model.id());
            assertEquals("medium", model.defaultReasoningEffort());
            assertEquals(List.of("text", "image"), model.inputModalities());
            assertEquals("priority", model.serviceTiers().getFirst().id());
        }
    }

    @Test
    void configuresInternalApiEndpointKeyAndRawOverrides() {
        var config = CodexClientConfig.builder()
                .command(List.of("codex", "app-server", "--listen", "stdio://"))
                .configOverrides(List.of("model=\"gpt-internal\"", "web_search=\"disabled\""))
                .baseUrl("http://internal.example/v1")
                .modelProvider("internal")
                .model("gpt-managed")
                .modelReasoningEffort(CodexClientConfig.ReasoningEffort.HIGH)
                .webSearch(CodexClientConfig.WebSearchMode.LIVE)
                .approvalPolicy(ThreadOptions.ApprovalPolicy.NEVER)
                .workspaceNetworkAccess(true)
                .apiKey("internal-key")
                .build();

        assertEquals(
                List.of(
                        "codex",
                        "--config",
                        "model=\"gpt-internal\"",
                        "--config",
                        "web_search=\"disabled\"",
                        "--config",
                        "openai_base_url=\"http://internal.example/v1\"",
                        "--config",
                        "model_provider=\"internal\"",
                        "--config",
                        "model=\"gpt-managed\"",
                        "--config",
                        "model_reasoning_effort=\"high\"",
                        "--config",
                        "web_search=\"live\"",
                        "--config",
                        "approval_policy=\"never\"",
                        "--config",
                        "sandbox_workspace_write.network_access=true",
                        "app-server",
                        "--listen",
                        "stdio://"),
                config.command());
        assertEquals("internal-key", config.environment().get("OPENAI_API_KEY"));
    }

    @Test
    void serializesAdditionalDirectoriesAsRuntimeWorkspaceRoots() {
        var additionalDirectory = Path.of(System.getProperty("java.io.tmpdir"))
                .toAbsolutePath()
                .resolve("codex-java-sdk-shared");
        var options = ThreadOptions.builder()
                .additionalDirectory(additionalDirectory)
                .skipGitRepoCheck(true)
                .build();

        var expectedRoots = JsonSupport.MAPPER.createArrayNode().add(additionalDirectory.toString());
        assertEquals(expectedRoots, options.toStartJson().path("runtimeWorkspaceRoots"));
        assertEquals(expectedRoots, options.toResumeJson().path("runtimeWorkspaceRoots"));
        assertEquals(expectedRoots, options.toForkJson().path("runtimeWorkspaceRoots"));
        assertFalse(options.toStartJson().has("skipGitRepoCheck"));
        assertTrue(options.skipGitRepoCheck());
    }

    @Test
    void cancellationTokenInterruptsRunningTurn() {
        var config = CodexClientConfig.builder()
                .command(mockServerCommand())
                .requestTimeout(Duration.ofSeconds(10))
                .build();

        try (var codex = CodexClient.create(config)) {
            var cancellation = new CancellationToken();
            var turn = codex.startThread().startTurn("wait cancellation", cancellation);
            assertTrue(cancellation.cancel());
            assertEquals("interrupted", turn.await().status());
            assertFalse(cancellation.cancel());
        }
    }

    @Test
    void cancelledTokenThrowsCancellationException() {
        var cancellation = new CancellationToken();
        cancellation.cancel();
        assertThrows(CancellationException.class, cancellation::throwIfCancelled);
    }

    @Test
    void preservesUnknownProtocolVariantsAsRawPayloads() throws Exception {
        var itemJson = JsonSupport.MAPPER.readTree(
                "{\"id\":\"future-1\",\"type\":\"futureItem\",\"newField\":42}");
        var item = assertInstanceOf(CodexItem.Unknown.class, CodexItem.from(itemJson));
        assertEquals(42, item.raw().path("newField").asInt());

        var params = JsonSupport.MAPPER.readTree("{\"futureField\":\"value\"}");
        var notification = assertInstanceOf(
                CodexNotification.Unknown.class,
                new CodexEvent("future/notification", params).notification());
        assertEquals(params, notification.raw());

        var progressParams = JsonSupport.MAPPER.readTree("""
                {
                  "threadId":"thread-1",
                  "turnId":"turn-1",
                  "itemId":"item-1",
                  "message":"working"
                }
                """);
        var progress = assertInstanceOf(
                CodexNotification.McpToolCallProgress.class,
                new CodexEvent("item/mcpToolCall/progress", progressParams).notification());
        assertEquals("working", progress.message());
    }

    @Test
    void retriesOnlyClassifiedOverloadErrors() {
        var config = CodexClientConfig.builder()
                .command(mockServerCommand())
                .requestTimeout(Duration.ofSeconds(10))
                .retryPolicy(new RetryPolicy(3, Duration.ZERO, Duration.ZERO, 1.0, 0.0))
                .build();

        try (var codex = CodexClient.create(config)) {
            assertEquals(3, codex.request("test/overload", null).path("attempts").asInt());
            assertThrows(
                    InvalidParamsException.class,
                    () -> codex.request("test/invalidParams", null));
        }
    }

    @Test
    void serializesCompleteThreadAndTurnOptions() throws Exception {
        var config = JsonSupport.MAPPER.readTree("{\"features\":{\"multi_agent\":true}}");
        var granular = ThreadOptions.GranularApprovalPolicy.builder()
                .mcpElicitations(true)
                .rules(true)
                .sandboxApproval(true)
                .requestPermissions(true)
                .skillApproval(false)
                .build();
        var threadOptions = ThreadOptions.builder()
                .model("gpt-5.5")
                .workingDirectory(Path.of("workspace"))
                .sandbox(ThreadOptions.Sandbox.WORKSPACE_WRITE)
                .granularApprovalPolicy(granular)
                .ephemeral(true)
                .developerInstructions("developer")
                .baseInstructions("base")
                .config(config)
                .modelProvider("openai")
                .personality(Personality.PRAGMATIC)
                .serviceName("demo")
                .serviceTier("priority")
                .threadSource("java-sdk")
                .lastTurnId("turn-previous")
                .build();
        var expectedStart = JsonSupport.MAPPER.readTree("""
                {
                  "model":"gpt-5.5",
                  "cwd":"workspace",
                  "sandbox":"workspace-write",
                  "baseInstructions":"base",
                  "developerInstructions":"developer",
                  "modelProvider":"openai",
                  "serviceTier":"priority",
                  "config":{"features":{"multi_agent":true}},
                  "approvalPolicy":{"granular":{
                    "mcp_elicitations":true,
                    "rules":true,
                    "sandbox_approval":true,
                    "request_permissions":true,
                    "skill_approval":false
                  }},
                  "ephemeral":true,
                  "personality":"pragmatic",
                  "serviceName":"demo",
                  "threadSource":"java-sdk"
                }
                """);
        assertEquals(expectedStart, threadOptions.toStartJson());

        var policy = SandboxPolicy.workspaceWrite()
                .writableRoot(Path.of("workspace"))
                .networkAccess(true)
                .excludeTmpdirEnvVar(true)
                .build();
        var turnOptions = TurnOptions.builder()
                .model("gpt-5.5")
                .workingDirectory(Path.of("workspace"))
                .reasoningEffort("high")
                .reasoningSummary(ReasoningSummary.DETAILED)
                .personality(Personality.FRIENDLY)
                .serviceTier("priority")
                .clientUserMessageId("message-1")
                .granularApprovalPolicy(granular)
                .sandboxPolicy(policy)
                .outputSchema(JsonSupport.MAPPER.readTree("{\"type\":\"object\"}"))
                .build();
        var expectedTurn = JsonSupport.MAPPER.readTree("""
                {
                  "model":"gpt-5.5",
                  "cwd":"workspace",
                  "effort":"high",
                  "clientUserMessageId":"message-1",
                  "personality":"friendly",
                  "serviceTier":"priority",
                  "summary":"detailed",
                  "outputSchema":{"type":"object"},
                  "approvalPolicy":{"granular":{
                    "mcp_elicitations":true,
                    "rules":true,
                    "sandbox_approval":true,
                    "request_permissions":true,
                    "skill_approval":false
                  }},
                  "sandboxPolicy":{
                    "type":"workspaceWrite",
                    "networkAccess":true,
                    "excludeTmpdirEnvVar":true,
                    "excludeSlashTmp":false,
                    "writableRoots":["workspace"]
                  }
                }
                """);
        assertEquals(expectedTurn, turnOptions.toJson());
    }

    private static List<String> mockServerCommand() {
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return List.of(java, "-cp", System.getProperty("java.class.path"), MockAppServer.class.getName());
    }

    private record ToolCompletion(boolean successful, String error, String status) {}
}
