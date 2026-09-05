// 此文件由 scripts/generate_protocol_types.py 自动生成，请勿手工修改。
// 来源：v2/ThreadTurnsListResponse.json
package io.github.majiajustar.codex.generated.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 一页会话 Turn 历史及其分页游标。
 *
 * @param backwardsCursor 反转 sortDirection 后可作为 cursor 使用的不透明游标；页面非空时提供。
 * @param data 当前页的 Turn 数据。
 * @param nextCursor 下一次调用使用的不透明游标；为 null 时表示没有更多 Turn。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThreadTurnsListResponse(
        String backwardsCursor,
        List<Turn> data,
        String nextCursor
) {}
