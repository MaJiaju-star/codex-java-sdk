package io.github.majiajustar.codex.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;

/** app-server 发起的强类型命令或文件变更审批请求。 */
public sealed interface ApprovalRequest permits ApprovalRequest.Command, ApprovalRequest.FileChange {
    /** 返回与该请求关联的会话、轮次和工具标识。 */
    ToolCallContext context();

    /** 返回 app-server 提供的可读原因；未提供时返回 {@code null}。 */
    String reason();

    /** 返回原始审批参数，供前向兼容场景检查。 */
    JsonNode raw();

    /**
     * Shell 命令审批请求。
     *
     * @param context 工具调用标识及命令元数据
     * @param reason 可读的审批原因；可能为 {@code null}
     * @param raw app-server 原始参数
     */
    record Command(ToolCallContext context, String reason, JsonNode raw) implements ApprovalRequest {}

    /**
     * 文件变更审批请求。
     *
     * @param context 工具调用标识及文件变更元数据
     * @param reason 可读的审批原因；可能为 {@code null}
     * @param grantRoot 可选择授予访问权限的目录
     * @param raw app-server 原始参数
     */
    record FileChange(ToolCallContext context, String reason, String grantRoot, JsonNode raw)
            implements ApprovalRequest {}

    /** app-server 审批协议支持的决策。 */
    enum Decision {
        ACCEPT("accept"),
        ACCEPT_FOR_SESSION("acceptForSession"),
        DECLINE("decline"),
        CANCEL("cancel");

        private final String wireValue;

        Decision(String wireValue) {
            this.wireValue = wireValue;
        }

        /** 返回发送到协议线上的驼峰命名值。 */
        public String wireValue() {
            return wireValue;
        }

        /**
         * 解析协议值；未知值会被视为拒绝，以保持“失败即关闭”的授权策略。
         *
         * @param value app-server 决策值
         * @return 匹配的决策；未知时返回 {@link #DECLINE}
         */
        public static Decision fromWireValue(String value) {
            return Arrays.stream(values())
                    .filter(decision -> decision.wireValue.equals(value))
                    .findFirst()
                    .orElse(DECLINE);
        }
    }

    public static ApprovalRequest from(String method, JsonNode params) {
        var context = ToolCallContext.fromApproval(method, params);
        var reason = params.path("reason").isTextual() ? params.path("reason").asText() : null;
        if (context.kind() == ToolCallContext.Kind.COMMAND) return new Command(context, reason, params);
        var grantRoot = params.path("grantRoot").isTextual() ? params.path("grantRoot").asText() : null;
        return new FileChange(context, reason, grantRoot, params);
    }
}
