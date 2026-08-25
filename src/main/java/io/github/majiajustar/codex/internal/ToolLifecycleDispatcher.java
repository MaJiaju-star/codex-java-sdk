package io.github.majiajustar.codex.internal;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.majiajustar.codex.CodexClientConfig;
import io.github.majiajustar.codex.event.CodexEvent;
import io.github.majiajustar.codex.event.CodexEventType;
import io.github.majiajustar.codex.exception.CodexException;
import io.github.majiajustar.codex.exception.CodexTimeoutException;
import io.github.majiajustar.codex.tool.ApprovalRequest;
import io.github.majiajustar.codex.tool.ToolCallContext;
import io.github.majiajustar.codex.tool.ToolCallResult;
import io.github.majiajustar.codex.tool.ToolInterceptor;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Routes approval requests and tool lifecycle events through configured SDK callbacks. */
public final class ToolLifecycleDispatcher implements AutoCloseable {
    private final CodexClientConfig config;
    private final ExecutorService callbackExecutor =
            Executors.newSingleThreadExecutor(Thread.ofVirtual().name("codex-tool-callback-", 0).factory());
    private final Map<String, ToolCallContext> activeTools = new ConcurrentHashMap<>();

    public ToolLifecycleDispatcher(CodexClientConfig config) {
        this.config = config;
    }

    public boolean handlesApproval(String method) {
        return method.equals("item/commandExecution/requestApproval")
                || method.equals("item/fileChange/requestApproval");
    }

    public JsonNode handleApproval(String method, JsonNode params) {
        var request = ApprovalRequest.from(method, params);
        activeTools.put(toolKey(request.context()), request.context());
        notifyApprovalRequested(request);
        var decision = ApprovalRequest.Decision.DECLINE;
        try {
            var decided = false;
            for (var interceptor : config.toolInterceptors()) {
                var result = await(interceptor.beforeToolCall(request));
                if (result instanceof ToolInterceptor.BeforeResult.Decide resolved) {
                    if (resolved.decision() != null) decision = resolved.decision();
                    decided = true;
                    break;
                }
            }
            if (!decided) {
                var handled = await(config.approvalHandler().requestApproval(request));
                if (handled != null) decision = handled;
            }
        } catch (RuntimeException ignored) {
            // Authorization callback failures fail closed.
        }
        return JsonSupport.MAPPER.createObjectNode().put("decision", decision.wireValue());
    }

    public void dispatch(CodexEvent event) {
        if (handlesEvent(event.type())) callbackExecutor.submit(() -> dispatchNow(event));
    }

    private void dispatchNow(CodexEvent event) {
        var params = event.params();
        if (event.type() == CodexEventType.ITEM_STARTED) {
            var context = ToolCallContext.fromItem(params);
            if (context.kind() == ToolCallContext.Kind.UNKNOWN) return;
            activeTools.put(toolKey(context), context);
            config.toolObservers().forEach(observer -> {
                try {
                    observer.onStarted(context);
                } catch (RuntimeException ignored) {
                    // Observer failures must not affect tool execution.
                }
            });
        } else if (event.type() == CodexEventType.COMMAND_OUTPUT_DELTA) {
            var context = activeTools.get(toolKey(params));
            if (context == null) context = commandContext(params);
            var resolvedContext = context;
            var delta = params.path("delta").asText("");
            config.toolObservers().forEach(observer -> {
                try {
                    observer.onOutput(resolvedContext, delta);
                } catch (RuntimeException ignored) {
                    // Observer failures must not affect tool execution.
                }
            });
        } else if (event.type() == CodexEventType.ITEM_COMPLETED) {
            dispatchCompletion(params);
        }
    }

    private void dispatchCompletion(JsonNode params) {
        var eventContext = ToolCallContext.fromItem(params);
        if (eventContext.kind() == ToolCallContext.Kind.UNKNOWN) return;
        var context = activeTools.remove(toolKey(eventContext));
        if (context == null) context = eventContext;
        var item = params.path("item");
        var errorNode = item.get("error");
        var error = errorNode == null || errorNode.isNull()
                ? null
                : errorNode.isTextual() ? errorNode.asText() : errorNode.toString();
        var status = item.path("status").asText();
        var result = new ToolCallResult(
                context,
                error == null && !status.equals("failed") && !status.equals("declined"),
                error,
                item);
        config.toolObservers().forEach(observer -> {
            try {
                observer.onCompleted(result);
            } catch (RuntimeException ignored) {
                // Observer failures must not affect tool execution.
            }
        });
        for (var index = config.toolInterceptors().size() - 1; index >= 0; index--) {
            try {
                await(config.toolInterceptors().get(index).afterToolCall(result));
            } catch (RuntimeException ignored) {
                // Completion interceptors cannot alter an already-completed call.
            }
        }
    }

    private void notifyApprovalRequested(ApprovalRequest request) {
        config.toolObservers().forEach(observer -> {
            try {
                observer.onApprovalRequested(request);
            } catch (RuntimeException ignored) {
                // Observer failures must not affect authorization.
            }
        });
    }

    private static boolean handlesEvent(CodexEventType type) {
        return type == CodexEventType.ITEM_STARTED
                || type == CodexEventType.ITEM_COMPLETED
                || type == CodexEventType.COMMAND_OUTPUT_DELTA;
    }

    private static String toolKey(ToolCallContext context) {
        return context.turnId() + "\u0000" + context.itemId();
    }

    private static String toolKey(JsonNode params) {
        return params.path("turnId").asText() + "\u0000" + params.path("itemId").asText();
    }

    private static ToolCallContext commandContext(JsonNode params) {
        return new ToolCallContext(
                params.path("threadId").asText(null),
                params.path("turnId").asText(null),
                params.path("itemId").asText(null),
                ToolCallContext.Kind.COMMAND,
                "commandExecution",
                null,
                null,
                params);
    }

    private <T> T await(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture()
                    .get(config.requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CodexException("Interrupted while waiting for callback", error);
        } catch (ExecutionException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new CodexException("Codex callback failed", error.getCause());
        } catch (TimeoutException error) {
            throw new CodexTimeoutException("Timed out waiting for callback", error);
        }
    }

    @Override
    public void close() {
        callbackExecutor.shutdownNow();
    }
}
