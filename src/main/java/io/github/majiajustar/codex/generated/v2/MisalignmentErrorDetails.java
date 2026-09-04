// 此文件由 scripts/generate_protocol_types.py 自动生成，请勿手工修改。
// 来源：v2/ThreadListResponse.json#/definitions/MisalignmentErrorDetails
package io.github.majiajustar.codex.generated.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 目标偏离拦截的公开说明和继续执行建议。
 *
 * @param detailedExplanation 向用户展示的本地化详细说明。
 * @param errorType 开放式目标偏离分类。
 * @param steer 确认继续执行时提交给下一轮的指令。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MisalignmentErrorDetails(
        String detailedExplanation,
        String errorType,
        MisalignmentSteer steer
) {}
