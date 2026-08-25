package io.github.majiajustar.codex.turn;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.majiajustar.codex.event.CodexItem;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.util.List;

/**
 * 已完成 Codex 轮次的汇总结果。
 *
 * @param id 轮次 ID
 * @param status app-server 终态
 * @param finalResponse Agent 最终回复；未生成时为 {@code null}
 * @param items 已完成原始轮次项的不可变列表
 * @param usage 最新的 Token 使用量载荷；可能为 {@code null}
 * @param turn 终态轮次的原始载荷
 */
public record TurnResult(
        String id,
        String status,
        String finalResponse,
        List<JsonNode> items,
        JsonNode usage,
        JsonNode turn) {
    /** Return completed items as typed variants with raw fallbacks. */
    public List<CodexItem> typedItems() {
        return CodexItem.fromAll(items);
    }

    /** Return structured token counters, or {@code null} when no usage event was received. */
    public TokenUsage typedUsage() {
        return TokenUsage.from(usage);
    }

    /** Return the terminal turn using the generated v2 protocol type. */
    public io.github.majiajustar.codex.generated.v2.Turn typedTurn() {
        return JsonSupport.MAPPER.convertValue(turn, io.github.majiajustar.codex.generated.v2.Turn.class);
    }
}
