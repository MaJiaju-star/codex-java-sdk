// 此文件由 scripts/generate_protocol_types.py 自动生成，请勿手工修改。
// 来源：v2/ThreadListResponse.json#/definitions/Thread
package io.github.majiajustar.codex.generated.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Codex 会话的强类型协议表示。
 *
 * @param agentNickname 由 AgentControl 创建的子 Agent 所获得的可选随机唯一昵称。
 * @param agentRole 由 AgentControl 创建的子 Agent 所获得的可选角色。
 * @param cliVersion 创建该会话的 Codex CLI 版本。
 * @param createdAt 会话创建时间，使用 Unix 秒级时间戳。
 * @param cwd 为该会话记录的工作目录。
 * @param ephemeral 该会话是否为临时会话且不应写入磁盘。
 * @param forkedFromId 该会话由其他会话派生时对应的源会话 ID。
 * @param gitInfo 创建会话时记录的可选 Git 元数据。
 * @param historyMode 会话历史的存储模式。
 * @param id 会话标识；Codex 生成的会话 ID 使用 UUIDv7。
 * @param model 该会话当前使用的模型标识。
 * @param modelProvider 该会话使用的模型提供方，例如 openai。
 * @param name 可选的用户可见会话标题。
 * @param parentThreadId 父会话 ID；只有当前会话是子 Agent 时才会设置。
 * @param path 会话在磁盘上的路径；该字段尚不稳定。
 * @param preview 会话预览，通常是第一条用户消息。
 * @param projectId projectId 协议字段的值。
 * @param reasoningEffort 该会话当前使用的推理强度。
 * @param recencyAt 用于会话新旧排序的 Unix 秒级时间戳。
 * @param section section 协议字段的值。
 * @param sectionEnteredAt sectionEnteredAt 协议字段的值。
 * @param sessionId 同一会话树中的多个会话共享的 Session ID。
 * @param source 会话来源，例如 CLI、VS Code、codex exec 或 codex app-server。
 * @param status 会话当前的运行状态。
 * @param threadSource 用于分析统计的可选会话来源分类。
 * @param turns 仅在 thread/resume、thread/rollback、thread/fork 和 thread/read （includeTurns 为
 *     true）响应中填充；其他响应和通知中为空列表。
 * @param updatedAt 会话最后更新时间，使用 Unix 秒级时间戳。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Thread(
        String agentNickname,
        String agentRole,
        String cliVersion,
        long createdAt,
        String cwd,
        boolean ephemeral,
        String forkedFromId,
        GitInfo gitInfo,
        ThreadHistoryMode historyMode,
        String id,
        String model,
        String modelProvider,
        String name,
        String parentThreadId,
        String path,
        String preview,
        String projectId,
        String reasoningEffort,
        Long recencyAt,
        JsonNode section,
        Long sectionEnteredAt,
        String sessionId,
        JsonNode source,
        ThreadStatus status,
        String threadSource,
        List<Turn> turns,
        long updatedAt
) {}
