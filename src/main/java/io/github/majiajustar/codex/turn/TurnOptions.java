package io.github.majiajustar.codex.turn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.majiajustar.codex.generated.v2.ApprovalsReviewer;
import io.github.majiajustar.codex.generated.v2.Personality;
import io.github.majiajustar.codex.generated.v2.ReasoningSummary;
import io.github.majiajustar.codex.internal.JsonSupport;
import io.github.majiajustar.codex.sandbox.SandboxPolicy;
import io.github.majiajustar.codex.thread.ThreadOptions;
import java.nio.file.Path;

/**
 * 完整的 v2 模型、推理、审批及沙箱覆盖配置。
 *
 * @param model 从该轮次及后续轮次开始使用的模型覆盖值
 * @param workingDirectory 从该轮次及后续轮次开始使用的工作目录覆盖值
 * @param reasoningEffort 从该轮次及后续轮次开始使用的推理强度覆盖值
 * @param sandbox 旧版粗粒度沙箱模式
 * @param approvalPolicy 旧版粗粒度审批策略
 * @param outputSchema 约束最终回复的 JSON Schema
 * @param approvalsReviewer 从该轮次及后续轮次开始使用的审批审核方
 * @param clientUserMessageId 调用方提供的消息关联 ID
 * @param personality 从该轮次及后续轮次开始使用的回复风格
 * @param sandboxPolicy 从该轮次及后续轮次开始使用的沙箱覆盖值
 * @param serviceTier 从该轮次及后续轮次开始使用的服务等级覆盖值
 * @param reasoningSummary 从该轮次及后续轮次开始使用的推理摘要覆盖值
 * @param granularApprovalPolicy 字段级审批策略
 */
public record TurnOptions(
        String model,
        Path workingDirectory,
        String reasoningEffort,
        ThreadOptions.Sandbox sandbox,
        ThreadOptions.ApprovalPolicy approvalPolicy,
        JsonNode outputSchema,
        ApprovalsReviewer approvalsReviewer,
        String clientUserMessageId,
        Personality personality,
        SandboxPolicy sandboxPolicy,
        String serviceTier,
        ReasoningSummary reasoningSummary,
        ThreadOptions.GranularApprovalPolicy granularApprovalPolicy) {

    /** 兼容最初仅包含六个轮次选项的构造方法。 */
    public TurnOptions(
            String model,
            Path workingDirectory,
            String reasoningEffort,
            ThreadOptions.Sandbox sandbox,
            ThreadOptions.ApprovalPolicy approvalPolicy,
            JsonNode outputSchema) {
        this(
                model,
                workingDirectory,
                reasoningEffort,
                sandbox,
                approvalPolicy,
                outputSchema,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public TurnOptions {
        if (approvalPolicy != null && granularApprovalPolicy != null) {
            throw new IllegalArgumentException(
                    "approvalPolicy and granularApprovalPolicy are mutually exclusive");
        }
        if (sandbox != null && sandboxPolicy != null) {
            throw new IllegalArgumentException("sandbox and sandboxPolicy are mutually exclusive");
        }
    }

    /** 返回不包含任何轮次覆盖项的选项。 */
    public static TurnOptions defaults() {
        return builder().build();
    }

    /** 返回空的轮次选项构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    public ObjectNode toJson() {
        var json = JsonSupport.MAPPER.createObjectNode();
        if (model != null) json.put("model", model);
        if (workingDirectory != null) json.put("cwd", workingDirectory.toString());
        if (reasoningEffort != null) json.put("effort", reasoningEffort);
        if (approvalsReviewer != null) json.put("approvalsReviewer", approvalsReviewer.wireValue());
        if (clientUserMessageId != null) json.put("clientUserMessageId", clientUserMessageId);
        if (personality != null) json.put("personality", personality.wireValue());
        if (serviceTier != null) json.put("serviceTier", serviceTier);
        if (reasoningSummary != null) json.put("summary", reasoningSummary.wireValue());
        if (outputSchema != null) json.set("outputSchema", outputSchema);
        if (granularApprovalPolicy != null) {
            json.set("approvalPolicy", granularApprovalPolicy.toJson());
        } else if (approvalPolicy != null) {
            json.put("approvalPolicy", approvalPolicy.wireValue());
        }
        if (sandboxPolicy != null) {
            json.set("sandboxPolicy", sandboxPolicy.toJson());
        } else if (sandbox != null) {
            json.set("sandboxPolicy", legacySandboxPolicy(sandbox));
        }
        return json;
    }

    private static ObjectNode legacySandboxPolicy(ThreadOptions.Sandbox sandbox) {
        return switch (sandbox) {
            case DANGER_FULL_ACCESS -> SandboxPolicy.dangerFullAccess().toJson();
            case READ_ONLY -> SandboxPolicy.readOnly().toJson();
            case WORKSPACE_WRITE -> SandboxPolicy.workspaceWrite().build().toJson();
        };
    }

    /** 用于构建不可变的轮次选项。 */
    public static final class Builder {
        private String model;
        private Path workingDirectory;
        private String reasoningEffort;
        private ThreadOptions.Sandbox sandbox;
        private ThreadOptions.ApprovalPolicy approvalPolicy;
        private JsonNode outputSchema;
        private ApprovalsReviewer approvalsReviewer;
        private String clientUserMessageId;
        private Personality personality;
        private SandboxPolicy sandboxPolicy;
        private String serviceTier;
        private ReasoningSummary reasoningSummary;
        private ThreadOptions.GranularApprovalPolicy granularApprovalPolicy;

        /** 设置从该轮次及后续轮次开始使用的模型覆盖值。 */
        public Builder model(String value) {
            model = value;
            return this;
        }

        /** 设置从该轮次及后续轮次开始使用的工作目录覆盖值。 */
        public Builder workingDirectory(Path value) {
            workingDirectory = value;
            return this;
        }

        /** 设置从该轮次及后续轮次开始使用的推理强度覆盖值。 */
        public Builder reasoningEffort(String value) {
            reasoningEffort = value;
            return this;
        }

        /** 设置从该轮次及后续轮次开始使用的旧版粗粒度沙箱模式。 */
        public Builder sandbox(ThreadOptions.Sandbox value) {
            sandbox = value;
            return this;
        }

        /** 设置从该轮次及后续轮次开始使用的 v2 沙箱覆盖值。 */
        public Builder sandboxPolicy(SandboxPolicy value) {
            sandboxPolicy = value;
            return this;
        }

        /** 设置从该轮次及后续轮次开始使用的粗粒度审批策略。 */
        public Builder approvalPolicy(ThreadOptions.ApprovalPolicy value) {
            approvalPolicy = value;
            return this;
        }

        /** 设置从该轮次及后续轮次开始使用的字段级审批策略。 */
        public Builder granularApprovalPolicy(ThreadOptions.GranularApprovalPolicy value) {
            granularApprovalPolicy = value;
            return this;
        }

        /** 设置用于约束最终回复的 JSON Schema。 */
        public Builder outputSchema(JsonNode value) {
            outputSchema = value;
            return this;
        }

        /** 设置从该轮次及后续轮次开始使用的审批审核方。 */
        public Builder approvalsReviewer(ApprovalsReviewer value) {
            approvalsReviewer = value;
            return this;
        }

        /** 设置调用方提供的用户消息关联 ID。 */
        public Builder clientUserMessageId(String value) {
            clientUserMessageId = value;
            return this;
        }

        /** 设置从该轮次及后续轮次开始使用的回复风格。 */
        public Builder personality(Personality value) {
            personality = value;
            return this;
        }

        /** 设置从该轮次及后续轮次开始使用的服务等级。 */
        public Builder serviceTier(String value) {
            serviceTier = value;
            return this;
        }

        /** 设置从该轮次及后续轮次开始使用的推理摘要模式。 */
        public Builder reasoningSummary(ReasoningSummary value) {
            reasoningSummary = value;
            return this;
        }

        /** 创建不可变的轮次选项。 */
        public TurnOptions build() {
            return new TurnOptions(
                    model,
                    workingDirectory,
                    reasoningEffort,
                    sandbox,
                    approvalPolicy,
                    outputSchema,
                    approvalsReviewer,
                    clientUserMessageId,
                    personality,
                    sandboxPolicy,
                    serviceTier,
                    reasoningSummary,
                    granularApprovalPolicy);
        }
    }
}
