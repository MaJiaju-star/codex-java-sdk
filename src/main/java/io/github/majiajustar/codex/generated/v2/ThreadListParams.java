// 此文件由 scripts/generate_protocol_types.py 自动生成，请勿手工修改。
// 来源：v2/ThreadListParams.json
package io.github.majiajustar.codex.generated.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 查询会话列表时使用的过滤和分页参数。
 *
 * @param archived 可选归档过滤器；true 仅返回已归档会话，否则仅返回未归档会话。
 * @param cursor 上一次调用返回的不透明分页游标。
 * @param cwd 可选工作目录过滤条件；只返回工作目录与任一指定路径完全一致的会话。
 * @param limit 可选页大小；省略时使用服务器默认值。
 * @param modelProviders 可选模型提供方过滤条件；存在但为空时包含所有提供方。
 * @param searchTerm 应用于会话标题的可选子字符串过滤条件。
 * @param sectionId sectionId 协议字段的值。
 * @param sortDirection 可选排序方向；默认按降序排列，最新记录优先。
 * @param sortKey 可选排序字段；默认为 created_at。
 * @param sourceKinds 可选来源过滤条件；省略或为空时默认包含交互式来源。
 * @param useStateDbOnly 为 true 时只读取状态数据库，不扫描 JSONL rollout 修复会话元数据；省略或为 false 时保留扫描及修复行为。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThreadListParams(
        Boolean archived,
        String cursor,
        JsonNode cwd,
        Integer limit,
        List<String> modelProviders,
        String searchTerm,
        String sectionId,
        SortDirection sortDirection,
        ThreadSortKey sortKey,
        List<ThreadSourceKind> sourceKinds,
        Boolean useStateDbOnly
) {}
