// 此文件由 scripts/generate_protocol_types.py 自动生成，请勿手工修改。
// 来源：v2/ThreadListResponse.json#/definitions/GitInfo
package io.github.majiajustar.codex.generated.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 创建会话时记录的 Git 仓库信息。
 *
 * @param branch 创建会话时所在的 Git 分支。
 * @param originUrl Git 远程 origin 的 URL。
 * @param sha 创建会话时检出的 Git 提交 SHA。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GitInfo(
        String branch,
        String originUrl,
        String sha
) {}
