// 此文件由 scripts/generate_protocol_types.py 自动生成，请勿手工修改。
// 来源：v2/ThreadListResponse.json#/definitions/Turn
package io.github.majiajustar.codex.generated.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Codex 轮次的强类型协议表示。
 *
 * @param completedAt 轮次完成时间，使用 Unix 秒级时间戳。
 * @param durationMs 已知时表示轮次从开始到完成所用的毫秒数。
 * @param error 只在轮次状态为失败时填充。
 * @param id 轮次标识；Codex 生成的轮次 ID 使用 UUIDv7。
 * @param items 当前包含在该轮次载荷中的会话项。
 * @param itemsView 说明该轮次已加载多少 items 数据。
 * @param startedAt 轮次开始时间，使用 Unix 秒级时间戳。
 * @param status 轮次当前状态。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Turn(
        Long completedAt,
        Long durationMs,
        TurnError error,
        String id,
        List<JsonNode> items,
        TurnItemsView itemsView,
        Long startedAt,
        TurnStatus status
) {}
