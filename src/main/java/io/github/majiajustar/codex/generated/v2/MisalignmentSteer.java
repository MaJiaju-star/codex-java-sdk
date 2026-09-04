// 此文件由 scripts/generate_protocol_types.py 自动生成，请勿手工修改。
// 来源：v2/ThreadListResponse.json#/definitions/MisalignmentSteer
package io.github.majiajustar.codex.generated.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 确认继续执行时提交给下一轮的用户指令。
 *
 * @param message 提交给下一轮的用户消息。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MisalignmentSteer(
        String message
) {}
