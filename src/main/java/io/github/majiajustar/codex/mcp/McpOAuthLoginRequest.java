package io.github.majiajustar.codex.mcp;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** 启动一个 MCP OAuth 登录流程所需的参数。 */
public record McpOAuthLoginRequest(
        String name, List<String> scopes, String threadId, Duration timeout) {
    public McpOAuthLoginRequest {
        if (Objects.requireNonNull(name, "name").isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        scopes = scopes == null ? null : List.copyOf(scopes);
        if (timeout != null && (timeout.isNegative() || timeout.getNano() != 0)) {
            throw new IllegalArgumentException("timeout must be a whole non-negative number of seconds");
        }
    }

    /** 创建不限定 scope 和线程的登录请求。 */
    public static McpOAuthLoginRequest forServer(String name) {
        return new McpOAuthLoginRequest(name, null, null, null);
    }

    /** 创建 OAuth 登录请求 Builder。 */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    ObjectNode toJson() {
        var json = JsonSupport.MAPPER.createObjectNode().put("name", name);
        if (scopes != null) scopes.forEach(json.putArray("scopes")::add);
        if (threadId != null) json.put("threadId", threadId);
        if (timeout != null) json.put("timeoutSecs", timeout.toSeconds());
        return json;
    }

    /** 构建带可选 scope、Thread 上下文和超时的 OAuth 登录请求。 */
    public static final class Builder {
        private final String name;
        private List<String> scopes;
        private String threadId;
        private Duration timeout;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        public Builder scopes(String... scopes) {
            this.scopes = List.of(scopes);
            return this;
        }

        public Builder threadId(String threadId) {
            this.threadId = threadId;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public McpOAuthLoginRequest build() {
            return new McpOAuthLoginRequest(name, scopes, threadId, timeout);
        }
    }
}
