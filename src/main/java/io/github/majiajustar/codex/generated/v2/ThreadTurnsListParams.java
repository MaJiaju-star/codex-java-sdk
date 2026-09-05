// 此文件由 scripts/generate_protocol_types.py 自动生成，请勿手工修改。
// 来源：v2/ThreadTurnsListParams.json
package io.github.majiajustar.codex.generated.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 分页读取指定会话 Turn 历史的请求参数。
 *
 * @param cursor 上一次调用返回的不透明分页游标。
 * @param itemsView 每个 Turn 返回的 Item 详情范围；省略时默认为摘要。
 * @param limit 可选页大小；app-server 0.153.0 最大支持 100。
 * @param sortDirection 可选排序方向；省略时默认按降序排列。
 * @param threadId 需要读取 Turn 历史的会话 ID。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThreadTurnsListParams(
        String cursor,
        TurnItemsView itemsView,
        Integer limit,
        SortDirection sortDirection,
        String threadId
) {}
