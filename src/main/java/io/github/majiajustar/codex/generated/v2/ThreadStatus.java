// 此文件由 scripts/generate_protocol_types.py 自动生成，请勿手工修改。
// 来源：v2/ThreadListResponse.json#/definitions/ThreadStatus
package io.github.majiajustar.codex.generated.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 会话状态的可辨识联合类型。
 *
 * @param type 会话当前状态
 * @param activeFlags 活动工作类别；只在会话处于活动状态时提供
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThreadStatus(
        ThreadStatusType type,
        List<ThreadActiveFlag> activeFlags
) {}
