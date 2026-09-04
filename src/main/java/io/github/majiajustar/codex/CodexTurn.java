package io.github.majiajustar.codex;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.majiajustar.codex.event.CodexEvent;
import io.github.majiajustar.codex.event.CodexEventType;
import io.github.majiajustar.codex.exception.CodexException;
import io.github.majiajustar.codex.internal.JsonSupport;
import io.github.majiajustar.codex.turn.TurnResult;
import io.github.majiajustar.codex.turn.UserInput;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 可流式订阅、追加指令、中断或等待完成的运行中轮次。
 *
 * <p>事件与汇总结果共享同一条内部流，因此每个轮次只能选择一种消费方式：通过 {@link #events()}
 * 订阅一次后再使用 {@link #resultAsync()}，或者不订阅事件而直接调用 {@link #resultAsync()} /
 * {@link #await()}。
 */
public final class CodexTurn {
    private final CodexClient client;
    private final String threadId;
    private final String id;
    private final EventChannel channel;
    private final ExecutorService executor;
    private final AtomicBoolean draining = new AtomicBoolean();
    private final CompletableFuture<TurnResult> result = new CompletableFuture<>();

    CodexTurn(CodexClient client, String threadId, String id, EventChannel channel, ExecutorService executor) {
        this.client = client;
        this.threadId = threadId;
        this.id = id;
        this.channel = channel;
        this.executor = executor;
    }

    /** 返回 app-server 轮次 ID。 */
    public String id() {
        return id;
    }

    /**
     * 返回仅允许单次订阅的发布器，并保留订阅前已经收到的事件。
     *
     * <p>第二个订阅者会收到 {@link IllegalStateException}。发布器将在终态
     * {@code turn/completed} 通知后结束。
     */
    public Flow.Publisher<CodexEvent> events() {
        return subscriber -> {
            var publisher = new SubmissionPublisher<CodexEvent>(executor, 256);
            publisher.subscribe(subscriber);
            if (!startDrain(publisher)) {
                publisher.closeExceptionally(new IllegalStateException("Turn events are already being consumed"));
            }
        };
    }

    /** 开始消费事件并返回包含汇总终态结果的 future。 */
    public CompletableFuture<TurnResult> resultAsync() {
        startDrain(null);
        return result;
    }

    /** 阻塞等待该轮次完成并返回汇总结果。 */
    public TurnResult await() {
        return resultAsync().join();
    }

    /**
     * 向当前运行中的轮次追加文本输入。
     *
     * @param prompt 追加的用户指令
     * @return 原始 {@code turn/steer} 响应
     */
    public JsonNode steer(String prompt) {
        var params = JsonSupport.MAPPER.createObjectNode()
                .put("threadId", threadId)
                .put("expectedTurnId", id);
        params.putArray("input").add(UserInput.text(prompt).toJson(JsonSupport.MAPPER));
        return client.request("turn/steer", params);
    }

    /** 请求中断该轮次。 */
    public void interrupt() {
        client.request("turn/interrupt", JsonSupport.MAPPER.createObjectNode()
                .put("threadId", threadId)
                .put("turnId", id));
    }

    /**
     * Interrupt this turn when the supplied token is cancelled.
     *
     * <p>The registration is removed automatically when the turn reaches a terminal state.
     */
    public CodexTurn cancelOn(CancellationToken token) {
        var registration = token.onCancel(() -> executor.submit(() -> {
            try {
                interrupt();
            } catch (RuntimeException error) {
                result.completeExceptionally(error);
            }
        }));
        result.whenComplete((ignoredResult, ignoredError) -> registration.close());
        return this;
    }

    private boolean startDrain(SubmissionPublisher<CodexEvent> publisher) {
        if (!draining.compareAndSet(false, true)) return false;
        executor.submit(() -> drain(publisher));
        return true;
    }

    private void drain(SubmissionPublisher<CodexEvent> publisher) {
        var items = new ArrayList<JsonNode>();
        JsonNode usage = null;
        String finalResponse = null;
        String unknownPhaseResponse = null;
        try {
            while (true) {
                var event = channel.take();
                if (publisher != null) publisher.submit(event);
                if (event.type() == CodexEventType.ITEM_COMPLETED) {
                    var item = event.params().path("item");
                    items.add(item);
                    if (item.path("type").asText().equals("agentMessage")) {
                        var phase = item.path("phase").asText();
                        if (phase.equals("final_answer")) {
                            finalResponse = item.path("text").asText();
                        } else if (phase.isEmpty()) {
                            unknownPhaseResponse = item.path("text").asText();
                        }
                    }
                } else if (event.type() == CodexEventType.TOKEN_USAGE_UPDATED) {
                    usage = event.params().get("tokenUsage");
                } else if (event.type() == CodexEventType.TURN_COMPLETED) {
                    var turn = event.params().path("turn");
                    var status = turn.path("status").asText();
                    if (status.equals("failed")) {
                        throw new CodexException(turn.path("error").path("message").asText("Codex turn failed"));
                    }
                    var response = finalResponse != null ? finalResponse : unknownPhaseResponse;
                    result.complete(new TurnResult(id, status, response, List.copyOf(items), usage, turn));
                    break;
                }
            }
            if (publisher != null) publisher.close();
        } catch (Throwable error) {
            result.completeExceptionally(error);
            if (publisher != null) publisher.closeExceptionally(error);
        }
    }

    static final class EventChannel {
        private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();

        void offer(CodexEvent event) {
            queue.offer(event);
        }

        void fail(Throwable error) {
            queue.offer(error);
        }

        CodexEvent take() throws InterruptedException {
            var item = queue.take();
            if (item instanceof CodexEvent event) return event;
            if (item instanceof RuntimeException error) throw error;
            if (item instanceof Throwable error) throw new CodexException("Turn event stream failed", error);
            throw new CodexException("Invalid turn event stream item");
        }
    }
}
