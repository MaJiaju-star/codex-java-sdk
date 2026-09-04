// 此文件由 scripts/generate_protocol_types.py 自动生成，请勿手工修改。
// 来源：v2/ThreadListResponse.json#/definitions/TurnError
package io.github.majiajustar.codex.generated.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 轮次失败时返回的结构化错误。
 *
 * @param additionalDetails 额外的错误详情。
 * @param codexErrorInfo Codex 提供的结构化错误信息。
 * @param message 可读的错误消息。
 * @param misalignment 目标偏离拦截的公开说明和继续执行建议。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TurnError(
        String additionalDetails,
        JsonNode codexErrorInfo,
        String message,
        MisalignmentErrorDetails misalignment
) {}
