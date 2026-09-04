package io.github.majiajustar.codex.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * app-server {@code ThreadItem} 的强类型视图。
 *
 * <p>常用 Item 变体会公开强类型字段；新增或尚未展开的变体使用 {@link Unknown} 表示，并保留
 * 原始载荷以支持协议前向兼容。
 */
public sealed interface CodexItem
        permits CodexItem.UserMessage,
                CodexItem.HookPrompt,
                CodexItem.AgentMessage,
                CodexItem.Plan,
                CodexItem.Reasoning,
                CodexItem.CommandExecution,
                CodexItem.FileChange,
                CodexItem.McpToolCall,
                CodexItem.DynamicToolCall,
                CodexItem.CollabAgentToolCall,
                CodexItem.SubAgentActivity,
                CodexItem.WebSearch,
                CodexItem.ImageView,
                CodexItem.Sleep,
                CodexItem.ImageGeneration,
                CodexItem.EnteredReviewMode,
                CodexItem.ExitedReviewMode,
                CodexItem.ContextCompaction,
                CodexItem.Unknown {
    /**
     * 返回 Codex 分配的 Item ID。
     *
     * @return Item ID
     */
    String id();

    /**
     * 返回 app-server 使用的准确 Item 判别值。
     *
     * @return Item 类型判别值
     */
    String type();

    /**
     * 返回完整原始载荷，其中包括新版 app-server 可能新增的字段。
     *
     * @return 原始 Item 载荷
     */
    JsonNode raw();

    /**
     * 解析一个 app-server Item，并保留未知变体和字段。
     *
     * @param item 原始 Item 载荷
     * @return 当前 SDK 能识别的最具体 Item 类型
     */
    static CodexItem from(JsonNode item) {
        var type = text(item, "type");
        var id = text(item, "id");
        return switch (type) {
            case "userMessage" -> new UserMessage(id, jsonList(item.path("content")), item);
            case "hookPrompt" -> new HookPrompt(id, jsonList(item.path("fragments")), item);
            case "agentMessage" -> new AgentMessage(
                    id,
                    text(item, "text"),
                    MessagePhase.fromWireValue(nullableText(item, "phase")),
                    item);
            case "plan" -> new Plan(id, text(item, "text"), item);
            case "reasoning" -> new Reasoning(
                    id,
                    stringList(item.path("summary")),
                    stringList(item.path("content")),
                    item);
            case "commandExecution" -> new CommandExecution(
                    id,
                    nullableText(item, "pluginId"),
                    nullableText(item, "scriptPath"),
                    text(item, "command"),
                    text(item, "cwd"),
                    nullableText(item, "processId"),
                    text(item, "source"),
                    text(item, "status"),
                    jsonList(item.path("commandActions")),
                    nullableText(item, "aggregatedOutput"),
                    nullableInteger(item, "exitCode"),
                    nullableLong(item, "durationMs"),
                    item);
            case "fileChange" -> new FileChange(
                    id, fileUpdates(item.path("changes")), text(item, "status"), item);
            case "mcpToolCall" -> new McpToolCall(
                    id,
                    text(item, "server"),
                    text(item, "tool"),
                    text(item, "status"),
                    item.get("arguments"),
                    nullableText(item, "pluginId"),
                    nullableBoolean(item, "readOnlyHint"),
                    item.get("result"),
                    item.get("error"),
                    nullableLong(item, "durationMs"),
                    item);
            case "dynamicToolCall" -> new DynamicToolCall(
                    id,
                    nullableText(item, "namespace"),
                    text(item, "tool"),
                    text(item, "status"),
                    item.get("arguments"),
                    jsonList(item.path("contentItems")),
                    nullableBoolean(item, "success"),
                    nullableLong(item, "durationMs"),
                    item);
            case "collabAgentToolCall" -> new CollabAgentToolCall(
                    id,
                    text(item, "senderThreadId"),
                    stringList(item.path("receiverThreadIds")),
                    text(item, "tool"),
                    text(item, "status"),
                    nullableText(item, "prompt"),
                    nullableText(item, "model"),
                    nullableText(item, "reasoningEffort"),
                    item.get("agentsStates"),
                    item);
            case "subAgentActivity" -> new SubAgentActivity(
                    id,
                    text(item, "agentThreadId"),
                    text(item, "agentPath"),
                    text(item, "kind"),
                    item);
            case "webSearch" -> new WebSearch(
                    id,
                    nullableText(item, "query"),
                    item.get("action"),
                    item.get("results"),
                    item);
            case "imageView" -> new ImageView(id, text(item, "path"), item);
            case "sleep" -> new Sleep(id, item.path("durationMs").asLong(), item);
            case "imageGeneration" -> new ImageGeneration(
                    id,
                    text(item, "status"),
                    text(item, "result"),
                    nullableText(item, "revisedPrompt"),
                    nullableText(item, "savedPath"),
                    nullableBoolean(item, "transparentBackground"),
                    item.get("failure"),
                    item);
            case "enteredReviewMode" ->
                new EnteredReviewMode(id, text(item, "review"), item);
            case "exitedReviewMode" -> new ExitedReviewMode(id, text(item, "review"), item);
            case "contextCompaction" -> new ContextCompaction(id, item);
            default -> new Unknown(id, type, item);
        };
    }

    /**
     * Hook 生成的提示片段。
     *
     * @param id Item ID
     * @param fragments Hook 生成的原始片段
     * @param raw 完整原始 Item 载荷
     */
    record HookPrompt(String id, List<JsonNode> fragments, JsonNode raw) implements CodexItem {
        public HookPrompt {
            fragments = List.copyOf(fragments);
        }

        @Override
        public String type() {
            return "hookPrompt";
        }
    }

    /**
     * 解析一组原始 Item 载荷。
     *
     * @param items 原始 Item 数组
     * @return 不可变的强类型 Item 列表
     */
    static List<CodexItem> fromAll(List<JsonNode> items) {
        return items.stream().map(CodexItem::from).toList();
    }

    /**
     * 作为 Turn Item 持久化的用户输入。
     *
     * @param id Item ID
     * @param content 用户输入内容
     * @param raw 完整原始 Item 载荷
     */
    record UserMessage(String id, List<JsonNode> content, JsonNode raw) implements CodexItem {
        public UserMessage {
            content = List.copyOf(content);
        }

        @Override
        public String type() {
            return "userMessage";
        }
    }

    /**
     * 助手文本，以及该文本属于过程说明还是最终回答。
     *
     * @param id Item ID
     * @param text 助手文本
     * @param phase 消息阶段
     * @param raw 完整原始 Item 载荷
     */
    record AgentMessage(String id, String text, MessagePhase phase, JsonNode raw)
            implements CodexItem {
        @Override
        public String type() {
            return "agentMessage";
        }
    }

    /**
     * 建议中或已完成的计划文本。
     *
     * @param id Item ID
     * @param text 计划文本
     * @param raw 完整原始 Item 载荷
     */
    record Plan(String id, String text, JsonNode raw) implements CodexItem {
        @Override
        public String type() {
            return "plan";
        }
    }

    /**
     * 模型推理摘要和正文片段。
     *
     * @param id Item ID
     * @param summary 推理摘要片段
     * @param content 推理正文片段
     * @param raw 完整原始 Item 载荷
     */
    record Reasoning(String id, List<String> summary, List<String> content, JsonNode raw)
            implements CodexItem {
        public Reasoning {
            summary = List.copyOf(summary);
            content = List.copyOf(content);
        }

        @Override
        public String type() {
            return "reasoning";
        }
    }

    /**
     * Shell 命令的生命周期和执行结果。
     *
     * @param id Item ID
     * @param pluginId 可选的来源插件 ID
     * @param scriptPath 可选的脚本路径
     * @param command 执行的命令
     * @param cwd 命令工作目录
     * @param processId 可选的进程 ID
     * @param source 命令来源
     * @param status 执行状态
     * @param commandActions Codex 识别出的命令动作
     * @param aggregatedOutput 聚合输出
     * @param exitCode 进程退出码
     * @param durationMs 执行耗时，单位为毫秒
     * @param raw 完整原始 Item 载荷
     */
    record CommandExecution(
            String id,
            String pluginId,
            String scriptPath,
            String command,
            String cwd,
            String processId,
            String source,
            String status,
            List<JsonNode> commandActions,
            String aggregatedOutput,
            Integer exitCode,
            Long durationMs,
            JsonNode raw)
            implements CodexItem {
        public CommandExecution {
            commandActions = List.copyOf(commandActions);
        }

        @Override
        public String type() {
            return "commandExecution";
        }
    }

    /** 单个文件更新所表示的操作类型。 */
    sealed interface PatchChangeKind {
        /**
         * 返回 app-server 使用的准确操作判别值。
         *
         * @return 操作判别值
         */
        String type();

        /** 新增文件。 */
        record Add() implements PatchChangeKind {
            @Override
            public String type() {
                return "add";
            }
        }

        /** 删除已有文件。 */
        record Delete() implements PatchChangeKind {
            @Override
            public String type() {
                return "delete";
            }
        }

        /**
         * 更新已有文件，并可同时移动文件。
         *
         * @param movePath 文件移动前的路径；未移动时为 {@code null}
         */
        record Update(String movePath) implements PatchChangeKind {
            @Override
            public String type() {
                return "update";
            }
        }

        /**
         * 新版 app-server 引入且当前 SDK 尚未识别的补丁操作。
         *
         * @param type app-server 实际发送的操作判别值
         * @param raw 完整原始操作载荷
         */
        record Unknown(String type, JsonNode raw) implements PatchChangeKind {}
    }

    /**
     * 文件变更 Item 中包含的单个文件更新。
     *
     * @param path 目标文件路径
     * @param kind 更新操作类型
     * @param diff 统一差异格式的补丁内容
     */
    record FileUpdate(String path, PatchChangeKind kind, String diff) {}

    /**
     * 文件补丁的生命周期和变更列表。
     *
     * @param id Item ID
     * @param changes 文件变更列表
     * @param status 执行状态
     * @param raw 完整原始 Item 载荷
     */
    record FileChange(String id, List<FileUpdate> changes, String status, JsonNode raw)
            implements CodexItem {
        public FileChange {
            changes = List.copyOf(changes);
        }

        @Override
        public String type() {
            return "fileChange";
        }
    }

    /**
     * MCP 工具调用及其结构化或原始结果。
     *
     * @param id Item ID
     * @param server MCP Server 名称
     * @param tool 工具名称
     * @param status 调用状态
     * @param arguments 调用参数
     * @param pluginId 可选的来源插件 ID
     * @param readOnlyHint 工具是否声明为只读
     * @param result 成功结果
     * @param error 错误结果
     * @param durationMs 调用耗时，单位为毫秒
     * @param raw 完整原始 Item 载荷
     */
    record McpToolCall(
            String id,
            String server,
            String tool,
            String status,
            JsonNode arguments,
            String pluginId,
            Boolean readOnlyHint,
            JsonNode result,
            JsonNode error,
            Long durationMs,
            JsonNode raw)
            implements CodexItem {
        @Override
        public String type() {
            return "mcpToolCall";
        }
    }

    /**
     * 客户端提供的动态工具调用。
     *
     * @param id Item ID
     * @param namespace 可选的工具命名空间
     * @param tool 工具名称
     * @param status 调用状态
     * @param arguments 调用参数
     * @param contentItems 工具返回的内容项
     * @param success 是否调用成功
     * @param durationMs 调用耗时，单位为毫秒
     * @param raw 完整原始 Item 载荷
     */
    record DynamicToolCall(
            String id,
            String namespace,
            String tool,
            String status,
            JsonNode arguments,
            List<JsonNode> contentItems,
            Boolean success,
            Long durationMs,
            JsonNode raw)
            implements CodexItem {
        public DynamicToolCall {
            contentItems = List.copyOf(contentItems);
        }

        @Override
        public String type() {
            return "dynamicToolCall";
        }
    }

    /**
     * 父 Agent 与一个或多个子 Agent 之间的协作调用。
     *
     * @param id Item ID
     * @param senderThreadId 发起调用的 Thread ID
     * @param receiverThreadIds 接收调用的 Thread ID 列表
     * @param tool 协作工具名称
     * @param status 调用状态
     * @param prompt 可选的提示内容
     * @param model 可选的模型名称
     * @param reasoningEffort 可选的推理强度
     * @param agentsStates 子 Agent 状态
     * @param raw 完整原始 Item 载荷
     */
    record CollabAgentToolCall(
            String id,
            String senderThreadId,
            List<String> receiverThreadIds,
            String tool,
            String status,
            String prompt,
            String model,
            String reasoningEffort,
            JsonNode agentsStates,
            JsonNode raw)
            implements CodexItem {
        public CollabAgentToolCall {
            receiverThreadIds = List.copyOf(receiverThreadIds);
        }

        @Override
        public String type() {
            return "collabAgentToolCall";
        }
    }

    /**
     * 子 Agent 发出的生命周期标记。
     *
     * @param id Item ID
     * @param agentThreadId 子 Agent 的 Thread ID
     * @param agentPath 子 Agent 路径
     * @param kind 活动类型
     * @param raw 完整原始 Item 载荷
     */
    record SubAgentActivity(
            String id, String agentThreadId, String agentPath, String kind, JsonNode raw)
            implements CodexItem {
        @Override
        public String type() {
            return "subAgentActivity";
        }
    }

    /**
     * Web 搜索请求及结果载荷。
     *
     * @param id Item ID
     * @param query 可选的搜索文本
     * @param action 搜索动作
     * @param results 搜索结果
     * @param raw 完整原始 Item 载荷
     */
    record WebSearch(String id, String query, JsonNode action, JsonNode results, JsonNode raw)
            implements CodexItem {
        @Override
        public String type() {
            return "webSearch";
        }
    }

    /**
     * Agent 查看过的本地图片。
     *
     * @param id Item ID
     * @param path 图片路径
     * @param raw 完整原始 Item 载荷
     */
    record ImageView(String id, String path, JsonNode raw) implements CodexItem {
        @Override
        public String type() {
            return "imageView";
        }
    }

    /**
     * Agent 主动等待的时间段。
     *
     * @param id Item ID
     * @param durationMs 等待时长，单位为毫秒
     * @param raw 完整原始 Item 载荷
     */
    record Sleep(String id, long durationMs, JsonNode raw) implements CodexItem {
        @Override
        public String type() {
            return "sleep";
        }
    }

    /**
     * 图片生成的生命周期和输出。
     *
     * @param id Item ID
     * @param status 生成状态
     * @param result 生成结果
     * @param revisedPrompt 可选的修订后提示词
     * @param savedPath 可选的保存路径
     * @param transparentBackground 是否使用透明背景
     * @param failure 失败详情
     * @param raw 完整原始 Item 载荷
     */
    record ImageGeneration(
            String id,
            String status,
            String result,
            String revisedPrompt,
            String savedPath,
            Boolean transparentBackground,
            JsonNode failure,
            JsonNode raw)
            implements CodexItem {
        @Override
        public String type() {
            return "imageGeneration";
        }
    }

    /**
     * Agent 已进入代码审查模式。
     *
     * @param id Item ID
     * @param review 审查说明
     * @param raw 完整原始 Item 载荷
     */
    record EnteredReviewMode(String id, String review, JsonNode raw) implements CodexItem {
        @Override
        public String type() {
            return "enteredReviewMode";
        }
    }

    /**
     * Agent 已退出代码审查模式。
     *
     * @param id Item ID
     * @param review 审查结果
     * @param raw 完整原始 Item 载荷
     */
    record ExitedReviewMode(String id, String review, JsonNode raw) implements CodexItem {
        @Override
        public String type() {
            return "exitedReviewMode";
        }
    }

    /**
     * Thread 上下文已完成压缩。
     *
     * @param id Item ID
     * @param raw 完整原始 Item 载荷
     */
    record ContextCompaction(String id, JsonNode raw) implements CodexItem {
        @Override
        public String type() {
            return "contextCompaction";
        }
    }

    /**
     * 未知或有意保持原始形式的 Item 变体。
     *
     * @param id Item ID
     * @param type app-server 实际发送的 Item 判别值
     * @param raw 完整原始 Item 载荷
     */
    record Unknown(String id, String type, JsonNode raw) implements CodexItem {}

    /** app-server 协议定义的助手消息阶段。 */
    enum MessagePhase {
        COMMENTARY,
        FINAL_ANSWER,
        UNSPECIFIED,
        UNKNOWN;

        static MessagePhase fromWireValue(String value) {
            if (value == null) return UNSPECIFIED;
            return switch (value) {
                case "commentary" -> COMMENTARY;
                case "final_answer" -> FINAL_ANSWER;
                default -> UNKNOWN;
            };
        }
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private static String nullableText(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Integer nullableInteger(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.intValue();
    }

    private static Long nullableLong(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.longValue();
    }

    private static Boolean nullableBoolean(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.booleanValue();
    }

    private static List<String> stringList(JsonNode values) {
        if (!values.isArray()) return List.of();
        var result = new ArrayList<String>();
        values.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private static List<JsonNode> jsonList(JsonNode values) {
        if (!values.isArray()) return List.of();
        var result = new ArrayList<JsonNode>();
        values.forEach(result::add);
        return List.copyOf(result);
    }

    static List<FileUpdate> fileUpdates(JsonNode values) {
        if (!values.isArray()) return List.of();
        var result = new ArrayList<FileUpdate>();
        values.forEach(value -> result.add(new FileUpdate(
                text(value, "path"), patchChangeKind(value.path("kind")), text(value, "diff"))));
        return List.copyOf(result);
    }

    private static PatchChangeKind patchChangeKind(JsonNode value) {
        var type = text(value, "type");
        return switch (type) {
            case "add" -> new PatchChangeKind.Add();
            case "delete" -> new PatchChangeKind.Delete();
            case "update" -> new PatchChangeKind.Update(nullableText(value, "move_path"));
            default -> new PatchChangeKind.Unknown(type, value);
        };
    }
}
