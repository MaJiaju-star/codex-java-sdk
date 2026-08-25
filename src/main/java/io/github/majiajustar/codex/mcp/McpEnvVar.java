package io.github.majiajustar.codex.mcp;

import java.util.Objects;

/** 传递给 stdio MCP Server 的环境变量引用。 */
public record McpEnvVar(String name, Source source) {
    public McpEnvVar {
        if (Objects.requireNonNull(name, "name").isBlank()) {
            throw new IllegalArgumentException("MCP environment variable name must not be blank");
        }
    }

    /** 继承当前执行环境中的变量。 */
    public static McpEnvVar inherit(String name) {
        return new McpEnvVar(name, null);
    }

    /** 从本地执行环境读取变量。 */
    public static McpEnvVar local(String name) {
        return new McpEnvVar(name, Source.LOCAL);
    }

    /** 从远程执行环境读取变量。 */
    public static McpEnvVar remote(String name) {
        return new McpEnvVar(name, Source.REMOTE);
    }

    /** 环境变量的来源。 */
    public enum Source {
        LOCAL("local"),
        REMOTE("remote");

        private final String wireValue;

        Source(String wireValue) {
            this.wireValue = wireValue;
        }

        /** 返回 Codex 配置使用的值。 */
        public String wireValue() {
            return wireValue;
        }
    }
}
