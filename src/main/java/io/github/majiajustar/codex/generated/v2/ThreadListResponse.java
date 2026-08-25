// 此文件由 scripts/generate_protocol_types.py 自动生成，请勿手工修改。
// 来源：v2/ThreadListResponse.json
package io.github.majiajustar.codex.generated.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 一页会话列表及其分页游标。
 *
 * @param backwardsCursor 反转 sortDirection 时传入 cursor
 *     的不透明游标。页面至少包含一个会话时才会提供；时间戳排序时会锚定页面起始时间，避免遗漏同一秒内的更新。
 * @param data 当前页的会话数据。
 * @param nextCursor 下一次调用使用的不透明游标；为 null 时表示没有更多数据。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThreadListResponse(
        String backwardsCursor,
        List<Thread> data,
        String nextCursor
) {}
