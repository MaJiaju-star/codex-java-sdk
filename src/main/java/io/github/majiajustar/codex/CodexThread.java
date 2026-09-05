package io.github.majiajustar.codex;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.majiajustar.codex.exception.CodexTransportException;
import io.github.majiajustar.codex.exception.InvalidRequestException;
import io.github.majiajustar.codex.generated.v2.SortDirection;
import io.github.majiajustar.codex.generated.v2.ThreadArchiveResponse;
import io.github.majiajustar.codex.generated.v2.ThreadUnarchiveResponse;
import io.github.majiajustar.codex.generated.v2.TurnItemsView;
import io.github.majiajustar.codex.goal.ThreadGoals;
import io.github.majiajustar.codex.internal.JsonSupport;
import io.github.majiajustar.codex.thread.ThreadHistory;
import io.github.majiajustar.codex.thread.ThreadOptions;
import io.github.majiajustar.codex.thread.ThreadTurnsListOptions;
import io.github.majiajustar.codex.thread.ThreadTurnsPage;
import io.github.majiajustar.codex.turn.TurnOptions;
import io.github.majiajustar.codex.turn.TurnResult;
import io.github.majiajustar.codex.turn.UserInput;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 包含一个或多个轮次的持久化 Codex 会话。
 *
 * <p>该对象只是一个轻量句柄。会话状态由 app-server 持有，之后可以通过 ID 恢复。
 */
public final class CodexThread {
    private static final int HISTORY_PAGE_LIMIT = 100;

    private final CodexClient client;
    private final String id;
    private final ThreadGoals goals;

    CodexThread(CodexClient client, String id) {
        this.client = client;
        this.id = id;
        goals = new ThreadGoals(client, id);
    }

    /** 返回稳定的 app-server 会话 ID。 */
    public String id() {
        return id;
    }

    /** 返回该会话的持久化 Goal API。 */
    public ThreadGoals goals() {
        return goals;
    }

    /** 启动文本轮次并阻塞等待其进入终态。 */
    public TurnResult run(String prompt) {
        return startTurn(prompt).await();
    }

    /** Run a text turn and connect its lifetime to a cooperative cancellation token. */
    public TurnResult run(String prompt, CancellationToken cancellationToken) {
        return startTurn(prompt, cancellationToken).await();
    }

    /** 启动包含多个输入项的轮次并阻塞等待其进入终态。 */
    public TurnResult run(List<UserInput> input, TurnOptions options) {
        return startTurn(input, options).await();
    }

    /** Run a turn and connect its lifetime to a cooperative cancellation token. */
    public TurnResult run(
            List<UserInput> input, TurnOptions options, CancellationToken cancellationToken) {
        cancellationToken.throwIfCancelled();
        return startTurn(input, options).cancelOn(cancellationToken).await();
    }

    /** 使用默认轮次选项启动流式文本轮次。 */
    public CodexTurn startTurn(String prompt) {
        return startTurn(List.of(UserInput.text(prompt)), TurnOptions.defaults());
    }

    /** Start a text turn and interrupt it when the cooperative token is cancelled. */
    public CodexTurn startTurn(String prompt, CancellationToken cancellationToken) {
        return startTurn(
                List.of(UserInput.text(prompt)), TurnOptions.defaults(), cancellationToken);
    }

    /**
     * 启动轮次，但不等待其完成。
     *
     * @param input 非空的用户输入列表
     * @param options 从该轮次开始生效的覆盖配置
     * @return 正在运行的轮次句柄
     */
    public CodexTurn startTurn(List<UserInput> input, TurnOptions options) {
        Objects.requireNonNull(input, "input");
        if (input.isEmpty()) throw new IllegalArgumentException("input must not be empty");
        return client.startTurn(id, List.copyOf(input), options);
    }

    /** Start a turn and interrupt it when the cooperative cancellation token is cancelled. */
    public CodexTurn startTurn(
            List<UserInput> input, TurnOptions options, CancellationToken cancellationToken) {
        cancellationToken.throwIfCancelled();
        return startTurn(input, options).cancelOn(cancellationToken);
    }

    /**
     * 读取原始持久化会话数据。
     *
     * @param includeTurns 响应中是否包含历史轮次
     * @return app-server 返回的原始 Thread 数据
     */
    public JsonNode read(boolean includeTurns) {
        return client.request("thread/read", JsonSupport.MAPPER.createObjectNode()
                .put("threadId", id)
                .put("includeTurns", includeTurns));
    }

    /**
     * 读取并解析当前会话的持久化历史。
     *
     * <p>普通历史通过 {@code thread/read} 读取；分页历史通过 {@code thread/turns/list}
     * 按时间正序聚合。缺少历史模式的旧服务端返回明确的分页错误时，也会自动回退到分页读取。
     * 返回的轮次包含强类型 {@code CodexItem}，完整原始载荷仍保留在各级 {@code raw()}
     * 中，以兼容新版 app-server 字段。
     *
     * @return 强类型会话历史
     */
    public ThreadHistory readHistory() {
        var metadataResponse = read(false);
        if (isPaginated(metadataResponse)) {
            return readPaginatedHistory(metadataResponse);
        }

        try {
            return ThreadHistory.fromResponse(read(true));
        } catch (InvalidRequestException error) {
            if (isPaginatedHistoryReadError(error)) {
                return readPaginatedHistory(metadataResponse);
            }
            throw error;
        }
    }

    /**
     * 使用服务器默认设置读取第一页 Turn 历史。
     *
     * @return 第一页强类型 Turn 历史
     */
    public ThreadTurnsPage listTurns() {
        return listTurns(ThreadTurnsListOptions.defaults());
    }

    /**
     * 分页读取当前会话的 Turn 历史。
     *
     * @param options 分页、排序和 Item 详情配置
     * @return 一页强类型 Turn 历史
     */
    public ThreadTurnsPage listTurns(ThreadTurnsListOptions options) {
        Objects.requireNonNull(options, "options");
        var params = JsonSupport.MAPPER.valueToTree(options.toParams(id));
        return ThreadTurnsPage.fromResponse(client.request("thread/turns/list", params));
    }

    private ThreadHistory readPaginatedHistory(JsonNode metadataResponse) {
        var turns = new ArrayList<ThreadHistory.Turn>();
        var seenCursors = new HashSet<String>();
        String cursor = null;
        while (true) {
            var options = ThreadTurnsListOptions.builder()
                    .cursor(cursor)
                    .limit(HISTORY_PAGE_LIMIT)
                    .sortDirection(SortDirection.ASC)
                    .itemsView(TurnItemsView.FULL)
                    .build();
            var page = listTurns(options);
            turns.addAll(page.data());
            var nextCursor = page.nextCursor();
            if (nextCursor == null) {
                return ThreadHistory.fromMetadataResponse(metadataResponse, turns);
            }
            if (!seenCursors.add(nextCursor)) {
                throw new CodexTransportException(
                        "thread/turns/list returned a repeated cursor: " + nextCursor);
            }
            cursor = nextCursor;
        }
    }

    private static boolean isPaginated(JsonNode metadataResponse) {
        return metadataResponse.path("thread").path("historyMode").asText().equals("paginated");
    }

    private static boolean isPaginatedHistoryReadError(InvalidRequestException error) {
        var message = error.rpcMessage().toLowerCase(Locale.ROOT);
        return message.contains("paginated threads")
                && message.contains("thread/read")
                && message.contains("includeturns=true");
    }

    /** 为该会话设置用户可见的名称。 */
    public void setName(String name) {
        client.request("thread/name/set", JsonSupport.MAPPER.createObjectNode()
                .put("threadId", id)
                .put("name", name));
    }

    /** 启动该会话的服务器端上下文压缩。 */
    public void compact() {
        client.request(
                "thread/compact/start",
                JsonSupport.MAPPER.createObjectNode().put("threadId", id));
    }

    /** 归档该会话。 */
    public ThreadArchiveResponse archive() {
        return client.archiveThread(id);
    }

    /** 从归档中恢复该会话。 */
    public ThreadUnarchiveResponse unarchive() {
        return client.unarchiveThread(id);
    }
}
