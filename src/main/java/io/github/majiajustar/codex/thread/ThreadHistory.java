package io.github.majiajustar.codex.thread;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.majiajustar.codex.event.CodexItem;
import io.github.majiajustar.codex.exception.CodexTransportException;
import io.github.majiajustar.codex.generated.v2.ThreadHistoryMode;
import io.github.majiajustar.codex.generated.v2.ThreadStatus;
import io.github.majiajustar.codex.generated.v2.TurnError;
import io.github.majiajustar.codex.generated.v2.TurnItemsView;
import io.github.majiajustar.codex.generated.v2.TurnStatus;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@code thread/read} 返回的强类型持久会话历史。
 *
 * <p>常用会话元数据、轮次和 Item 已转换为强类型；{@link #raw()} 和 {@link Turn#raw()}
 * 保留完整协议载荷，以便调用方读取新版 app-server 增加而当前 SDK 尚未建模的字段。
 *
 * @param id Thread ID
 * @param sessionId 同一会话树共享的 Session ID
 * @param name 用户可见的会话名称
 * @param preview 会话预览，通常来自第一条用户消息
 * @param cwd 会话工作目录
 * @param model 当前模型标识
 * @param modelProvider 当前模型提供方
 * @param status 会话状态
 * @param historyMode 会话历史存储模式
 * @param createdAt 创建时间，使用 Unix 秒级时间戳
 * @param updatedAt 最后更新时间，使用 Unix 秒级时间戳
 * @param recencyAt 用于会话排序的 Unix 秒级时间戳
 * @param turns 按 app-server 返回顺序排列的轮次
 * @param raw 完整的 {@code thread/read} 响应载荷
 */
public record ThreadHistory(
        String id,
        String sessionId,
        String name,
        String preview,
        String cwd,
        String model,
        String modelProvider,
        ThreadStatus status,
        ThreadHistoryMode historyMode,
        long createdAt,
        long updatedAt,
        Long recencyAt,
        List<Turn> turns,
        JsonNode raw) {

    /** 创建不可变的会话历史。 */
    public ThreadHistory {
        turns = List.copyOf(turns);
    }

    /**
     * 将原始 {@code thread/read} 响应转换为强类型历史。
     *
     * @param response app-server 返回的完整响应结果
     * @return 强类型会话历史
     * @throws CodexTransportException 响应缺少 Thread 或无法按当前协议解析时抛出
     */
    public static ThreadHistory fromResponse(JsonNode response) {
        Objects.requireNonNull(response, "response");
        var threadNode = response.get("thread");
        if (threadNode == null || !threadNode.isObject()) {
            throw new CodexTransportException("thread/read response is missing thread");
        }

        try {
            var protocolThread = JsonSupport.MAPPER.treeToValue(
                    threadNode, io.github.majiajustar.codex.generated.v2.Thread.class);
            var protocolTurns = protocolThread.turns() == null
                    ? List.<io.github.majiajustar.codex.generated.v2.Turn>of()
                    : protocolThread.turns();
            var rawTurns = threadNode.path("turns");
            var turns = new ArrayList<Turn>(protocolTurns.size());
            for (int index = 0; index < protocolTurns.size(); index++) {
                var protocolTurn = protocolTurns.get(index);
                var rawTurn = rawTurns.isArray() && index < rawTurns.size()
                        ? rawTurns.get(index)
                        : JsonSupport.MAPPER.createObjectNode();
                var rawItems = protocolTurn.items() == null
                        ? List.<JsonNode>of()
                        : protocolTurn.items();
                turns.add(new Turn(
                        protocolTurn.id(),
                        protocolTurn.status(),
                        protocolTurn.startedAt(),
                        protocolTurn.completedAt(),
                        protocolTurn.durationMs(),
                        protocolTurn.error(),
                        protocolTurn.itemsView(),
                        CodexItem.fromAll(rawItems),
                        rawTurn));
            }

            return new ThreadHistory(
                    protocolThread.id(),
                    protocolThread.sessionId(),
                    protocolThread.name(),
                    protocolThread.preview(),
                    protocolThread.cwd(),
                    protocolThread.model(),
                    protocolThread.modelProvider(),
                    protocolThread.status(),
                    protocolThread.historyMode(),
                    protocolThread.createdAt(),
                    protocolThread.updatedAt(),
                    protocolThread.recencyAt(),
                    turns,
                    response);
        } catch (IOException | IllegalArgumentException error) {
            throw new CodexTransportException("Unable to decode thread/read response", error);
        }
    }

    /**
     * 会话历史中的一个强类型轮次。
     *
     * @param id Turn ID
     * @param status Turn 状态
     * @param startedAt 开始时间，使用 Unix 秒级时间戳
     * @param completedAt 完成时间，使用 Unix 秒级时间戳
     * @param durationMs 执行时长，单位为毫秒
     * @param error 失败时的结构化错误
     * @param itemsView Item 的加载范围
     * @param items 当前轮次包含的强类型 Item
     * @param raw 完整原始 Turn 载荷
     */
    public record Turn(
            String id,
            TurnStatus status,
            Long startedAt,
            Long completedAt,
            Long durationMs,
            TurnError error,
            TurnItemsView itemsView,
            List<CodexItem> items,
            JsonNode raw) {

        /** 创建不可变的历史轮次。 */
        public Turn {
            items = List.copyOf(items);
        }
    }
}
