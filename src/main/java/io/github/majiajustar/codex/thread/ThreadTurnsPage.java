package io.github.majiajustar.codex.thread;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.majiajustar.codex.exception.CodexTransportException;
import io.github.majiajustar.codex.generated.v2.ThreadTurnsListResponse;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@code thread/turns/list} 返回的一页强类型 Turn 历史。
 *
 * @param data 当前页的强类型 Turn
 * @param nextCursor 下一页游标；{@code null} 表示已经到达末页
 * @param backwardsCursor 反转排序方向时可使用的回溯游标
 * @param raw 完整原始响应载荷
 */
public record ThreadTurnsPage(
        List<ThreadHistory.Turn> data,
        String nextCursor,
        String backwardsCursor,
        JsonNode raw) {

    /** 创建不可变的 Turn 分页结果。 */
    public ThreadTurnsPage {
        data = List.copyOf(data);
    }

    /**
     * 将原始 {@code thread/turns/list} 响应转换为强类型页面。
     *
     * @param response app-server 返回的完整响应结果
     * @return 强类型 Turn 页面
     * @throws CodexTransportException 响应无法按当前协议解析时抛出
     */
    public static ThreadTurnsPage fromResponse(JsonNode response) {
        Objects.requireNonNull(response, "response");
        try {
            var protocolPage = JsonSupport.MAPPER.treeToValue(
                    response, ThreadTurnsListResponse.class);
            var protocolTurns = protocolPage.data() == null
                    ? List.<io.github.majiajustar.codex.generated.v2.Turn>of()
                    : protocolPage.data();
            var rawTurns = response.path("data");
            var turns = new ArrayList<ThreadHistory.Turn>(protocolTurns.size());
            for (int index = 0; index < protocolTurns.size(); index++) {
                var rawTurn = rawTurns.isArray() && index < rawTurns.size()
                        ? rawTurns.get(index)
                        : JsonSupport.MAPPER.createObjectNode();
                turns.add(ThreadHistory.Turn.fromProtocol(protocolTurns.get(index), rawTurn));
            }
            return new ThreadTurnsPage(
                    turns,
                    protocolPage.nextCursor(),
                    protocolPage.backwardsCursor(),
                    response);
        } catch (IOException | IllegalArgumentException error) {
            throw new CodexTransportException(
                    "Unable to decode thread/turns/list response", error);
        }
    }
}
