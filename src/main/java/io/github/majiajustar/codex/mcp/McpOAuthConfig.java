package io.github.majiajustar.codex.mcp;

import java.util.Objects;

/** Codex 启动 MCP OAuth 流程时使用的客户端配置。 */
public record McpOAuthConfig(String clientId, Integer callbackPort) {
    public McpOAuthConfig {
        if (clientId != null && clientId.isBlank()) {
            throw new IllegalArgumentException("MCP OAuth clientId must not be blank");
        }
        if (callbackPort != null && (callbackPort < 0 || callbackPort > 65535)) {
            throw new IllegalArgumentException("MCP OAuth callbackPort must be between 0 and 65535");
        }
    }

    /** 创建 OAuth 配置 Builder。 */
    public static Builder builder() {
        return new Builder();
    }

    /** 用于构建不可变 OAuth 配置。 */
    public static final class Builder {
        private String clientId;
        private Integer callbackPort;

        public Builder clientId(String clientId) {
            this.clientId = Objects.requireNonNull(clientId, "clientId");
            return this;
        }

        public Builder callbackPort(int callbackPort) {
            this.callbackPort = callbackPort;
            return this;
        }

        public McpOAuthConfig build() {
            return new McpOAuthConfig(clientId, callbackPort);
        }
    }
}
