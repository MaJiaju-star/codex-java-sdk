package io.github.majiajustar.codex.thread;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.majiajustar.codex.generated.v2.ApprovalsReviewer;
import io.github.majiajustar.codex.generated.v2.Personality;
import io.github.majiajustar.codex.generated.v2.ThreadStartSource;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动、恢复或派生会话时使用的完整 v2 选项。
 *
 * <p>并非每个字段都适用于全部操作；SDK 只会发送 {@code thread/start}、
 * {@code thread/resume} 或 {@code thread/fork} 所接受的字段。
 *
 * @param model 模型覆盖值
 * @param workingDirectory 记录到会话中的工作目录
 * @param sandbox 旧版粗粒度沙箱模式
 * @param approvalPolicy 旧版粗粒度审批策略
 * @param ephemeral 启动或派生的会话是否不进行持久化
 * @param developerInstructions 追加到会话上下文的开发者指令
 * @param approvalsReviewer 负责审批决策的审核方
 * @param baseInstructions 替换后的基础指令
 * @param config 原始配置覆盖项
 * @param modelProvider 模型提供方标识
 * @param personality 回复风格
 * @param serviceName 关联到新会话的服务名称
 * @param serviceTier 推理服务等级
 * @param sessionStartSource 新会话的启动来源
 * @param threadSource 保持前向兼容的原始会话来源
 * @param granularApprovalPolicy 字段级审批策略
 * @param lastTurnId 派生时包含的最后一个源轮次 ID
 * @param additionalDirectories 额外的绝对运行时工作区根目录
 * @param skipGitRepoCheck exec 兼容选项；app-server 本身不执行 Git 仓库预检
 */
public record ThreadOptions(
        String model,
        Path workingDirectory,
        Sandbox sandbox,
        ApprovalPolicy approvalPolicy,
        Boolean ephemeral,
        String developerInstructions,
        ApprovalsReviewer approvalsReviewer,
        String baseInstructions,
        JsonNode config,
        String modelProvider,
        Personality personality,
        String serviceName,
        String serviceTier,
        ThreadStartSource sessionStartSource,
        String threadSource,
        GranularApprovalPolicy granularApprovalPolicy,
        String lastTurnId,
        List<Path> additionalDirectories,
        Boolean skipGitRepoCheck) {

    /** 兼容最初仅包含六个会话选项的构造方法。 */
    public ThreadOptions(
            String model,
            Path workingDirectory,
            Sandbox sandbox,
            ApprovalPolicy approvalPolicy,
            Boolean ephemeral,
            String developerInstructions) {
        this(
                model,
                workingDirectory,
                sandbox,
                approvalPolicy,
                ephemeral,
                developerInstructions,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public ThreadOptions {
        additionalDirectories = additionalDirectories == null
                ? null
                : List.copyOf(additionalDirectories);
        if (additionalDirectories != null
                && additionalDirectories.stream().anyMatch(path -> !path.isAbsolute())) {
            throw new IllegalArgumentException("additionalDirectories must contain absolute paths");
        }
        if (approvalPolicy != null && granularApprovalPolicy != null) {
            throw new IllegalArgumentException(
                    "approvalPolicy and granularApprovalPolicy are mutually exclusive");
        }
    }

    /** 返回不包含任何覆盖项的选项。 */
    public static ThreadOptions defaults() {
        return builder().build();
    }

    /** 返回空的会话选项构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    public ObjectNode toStartJson() {
        var json = commonJson();
        putNullable(json, "ephemeral", ephemeral);
        putNullable(json, "personality", personality == null ? null : personality.wireValue());
        putNullable(json, "serviceName", serviceName);
        putNullable(
                json,
                "sessionStartSource",
                sessionStartSource == null ? null : sessionStartSource.wireValue());
        putNullable(json, "threadSource", threadSource);
        return json;
    }

    public ObjectNode toResumeJson() {
        var json = commonJson();
        putNullable(json, "personality", personality == null ? null : personality.wireValue());
        return json;
    }

    public ObjectNode toForkJson() {
        var json = commonJson();
        putNullable(json, "ephemeral", ephemeral);
        putNullable(json, "lastTurnId", lastTurnId);
        putNullable(json, "threadSource", threadSource);
        return json;
    }

    public ObjectNode toJson() {
        return toStartJson();
    }

    private ObjectNode commonJson() {
        var json = JsonSupport.MAPPER.createObjectNode();
        putNullable(json, "model", model);
        putNullable(json, "cwd", workingDirectory == null ? null : workingDirectory.toString());
        putNullable(json, "sandbox", sandbox == null ? null : sandbox.wireValue);
        putNullable(json, "baseInstructions", baseInstructions);
        putNullable(json, "developerInstructions", developerInstructions);
        putNullable(json, "modelProvider", modelProvider);
        putNullable(json, "serviceTier", serviceTier);
        if (additionalDirectories != null) {
            var roots = json.putArray("runtimeWorkspaceRoots");
            additionalDirectories.stream().map(Path::toString).forEach(roots::add);
        }
        if (approvalsReviewer != null) json.put("approvalsReviewer", approvalsReviewer.wireValue());
        if (config != null) json.set("config", config);
        if (granularApprovalPolicy != null) {
            json.set("approvalPolicy", granularApprovalPolicy.toJson());
        } else if (approvalPolicy != null) {
            json.put("approvalPolicy", approvalPolicy.wireValue);
        }
        return json;
    }

    private static void putNullable(ObjectNode target, String name, String value) {
        if (value != null) target.put(name, value);
    }

    private static void putNullable(ObjectNode target, String name, Boolean value) {
        if (value != null) target.put(name, value);
    }

    /** 会话级请求接受的旧版粗粒度沙箱模式。 */
    public enum Sandbox {
        READ_ONLY("read-only"),
        WORKSPACE_WRITE("workspace-write"),
        DANGER_FULL_ACCESS("danger-full-access");

        private final String wireValue;

        Sandbox(String wireValue) {
            this.wireValue = wireValue;
        }

        /** 返回 app-server 使用的短横线命名协议值。 */
        public String wireValue() {
            return wireValue;
        }
    }

    /** 会话级请求接受的旧版粗粒度审批策略。 */
    public enum ApprovalPolicy {
        UNTRUSTED("untrusted"),
        ON_REQUEST("on-request"),
        NEVER("never");

        private final String wireValue;

        ApprovalPolicy(String wireValue) {
            this.wireValue = wireValue;
        }

        /** 返回 app-server 使用的短横线命名协议值。 */
        public String wireValue() {
            return wireValue;
        }
    }

    /**
     * 在单一粗粒度策略不足时使用的字段级 v2 审批策略。
     *
     * @param mcpElicitations MCP 信息征询请求是否需要审批
     * @param rules 基于规则的操作是否需要审批
     * @param sandboxApproval 沙箱权限提升是否需要审批
     * @param requestPermissions 明确的权限请求是否需要审批
     * @param skillApproval Skill 执行是否需要审批
     */
    public record GranularApprovalPolicy(
            boolean mcpElicitations,
            boolean rules,
            boolean sandboxApproval,
            boolean requestPermissions,
            boolean skillApproval) {

        public ObjectNode toJson() {
            var granular = JsonSupport.MAPPER.createObjectNode()
                    .put("mcp_elicitations", mcpElicitations)
                    .put("rules", rules)
                    .put("sandbox_approval", sandboxApproval)
                    .put("request_permissions", requestPermissions)
                    .put("skill_approval", skillApproval);
            return JsonSupport.MAPPER.createObjectNode().set("granular", granular);
        }

        /** 返回字段级审批策略构建器。 */
        public static Builder builder() {
            return new Builder();
        }

        /** 用于构建不可变的字段级审批策略。 */
        public static final class Builder {
            private boolean mcpElicitations;
            private boolean rules;
            private boolean sandboxApproval;
            private boolean requestPermissions;
            private boolean skillApproval;

            /** 配置 MCP 信息征询审批。 */
            public Builder mcpElicitations(boolean value) {
                mcpElicitations = value;
                return this;
            }

            /** 配置规则审批。 */
            public Builder rules(boolean value) {
                rules = value;
                return this;
            }

            /** 配置沙箱权限提升审批。 */
            public Builder sandboxApproval(boolean value) {
                sandboxApproval = value;
                return this;
            }

            /** 配置明确的权限请求审批。 */
            public Builder requestPermissions(boolean value) {
                requestPermissions = value;
                return this;
            }

            /** 配置 Skill 审批。 */
            public Builder skillApproval(boolean value) {
                skillApproval = value;
                return this;
            }

            /** 创建不可变的字段级审批策略。 */
            public GranularApprovalPolicy build() {
                return new GranularApprovalPolicy(
                        mcpElicitations,
                        rules,
                        sandboxApproval,
                        requestPermissions,
                        skillApproval);
            }
        }
    }

    /** 用于构建不可变的会话选项。 */
    public static final class Builder {
        private String model;
        private Path workingDirectory;
        private Sandbox sandbox;
        private ApprovalPolicy approvalPolicy;
        private Boolean ephemeral;
        private String developerInstructions;
        private ApprovalsReviewer approvalsReviewer;
        private String baseInstructions;
        private JsonNode config;
        private String modelProvider;
        private Personality personality;
        private String serviceName;
        private String serviceTier;
        private ThreadStartSource sessionStartSource;
        private String threadSource;
        private GranularApprovalPolicy granularApprovalPolicy;
        private String lastTurnId;
        private List<Path> additionalDirectories;
        private Boolean skipGitRepoCheck;

        /** 设置模型覆盖值。 */
        public Builder model(String value) {
            model = value;
            return this;
        }

        /** 设置会话工作目录。 */
        public Builder workingDirectory(Path value) {
            workingDirectory = value;
            return this;
        }

        /** 设置旧版粗粒度沙箱模式。 */
        public Builder sandbox(Sandbox value) {
            sandbox = value;
            return this;
        }

        /** 设置旧版粗粒度审批策略。 */
        public Builder approvalPolicy(ApprovalPolicy value) {
            approvalPolicy = value;
            return this;
        }

        /** 设置字段级审批策略，并替代粗粒度策略。 */
        public Builder granularApprovalPolicy(GranularApprovalPolicy value) {
            granularApprovalPolicy = value;
            return this;
        }

        /** 控制启动或派生的会话是否持久化。 */
        public Builder ephemeral(boolean value) {
            ephemeral = value;
            return this;
        }

        /** 设置会话的开发者指令。 */
        public Builder developerInstructions(String value) {
            developerInstructions = value;
            return this;
        }

        /** 设置审批审核方。 */
        public Builder approvalsReviewer(ApprovalsReviewer value) {
            approvalsReviewer = value;
            return this;
        }

        /** 替换基础指令。 */
        public Builder baseInstructions(String value) {
            baseInstructions = value;
            return this;
        }

        /** 设置原始 app-server 配置覆盖项。 */
        public Builder config(JsonNode value) {
            config = value;
            return this;
        }

        /** 设置模型提供方标识。 */
        public Builder modelProvider(String value) {
            modelProvider = value;
            return this;
        }

        /** 设置回复风格。 */
        public Builder personality(Personality value) {
            personality = value;
            return this;
        }

        /** 设置新会话的服务名称。 */
        public Builder serviceName(String value) {
            serviceName = value;
            return this;
        }

        /** 设置推理服务等级。 */
        public Builder serviceTier(String value) {
            serviceTier = value;
            return this;
        }

        /** 设置新会话的启动来源。 */
        public Builder sessionStartSource(ThreadStartSource value) {
            sessionStartSource = value;
            return this;
        }

        /** 设置保持前向兼容的原始会话来源。 */
        public Builder threadSource(String value) {
            threadSource = value;
            return this;
        }

        /** 选择派生时包含的最后一个源轮次。 */
        public Builder lastTurnId(String value) {
            lastTurnId = value;
            return this;
        }

        /** Add an absolute runtime workspace root, equivalent to exec {@code --add-dir}. */
        public Builder additionalDirectory(Path value) {
            if (additionalDirectories == null) additionalDirectories = new ArrayList<>();
            additionalDirectories.add(value);
            return this;
        }

        /** Add absolute runtime workspace roots, equivalent to repeated exec {@code --add-dir}. */
        public Builder additionalDirectories(List<Path> values) {
            if (additionalDirectories == null) additionalDirectories = new ArrayList<>();
            additionalDirectories.addAll(values);
            return this;
        }

        /**
         * Accept the exec compatibility option.
         *
         * <p>App-server does not perform the {@code codex exec} Git repository preflight, so this
         * value requires no wire field and does not change app-server behavior.
         */
        public Builder skipGitRepoCheck(boolean value) {
            skipGitRepoCheck = value;
            return this;
        }

        /** 创建不可变的会话选项。 */
        public ThreadOptions build() {
            return new ThreadOptions(
                    model,
                    workingDirectory,
                    sandbox,
                    approvalPolicy,
                    ephemeral,
                    developerInstructions,
                    approvalsReviewer,
                    baseInstructions,
                    config,
                    modelProvider,
                    personality,
                    serviceName,
                    serviceTier,
                    sessionStartSource,
                    threadSource,
                    granularApprovalPolicy,
                    lastTurnId,
                    additionalDirectories,
                    skipGitRepoCheck);
        }
    }
}
