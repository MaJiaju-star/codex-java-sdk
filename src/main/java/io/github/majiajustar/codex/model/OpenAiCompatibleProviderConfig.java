package io.github.majiajustar.codex.model;

import java.net.URI;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 使用 Responses API 和环境变量 API Key 的 OpenAI-compatible 模型提供方配置。
 *
 * @param id Codex 配置中的模型提供方 ID
 * @param name 便于展示的提供方名称
 * @param baseUrl OpenAI-compatible API 的基础地址
 * @param apiKeyEnvironmentVariable Codex 应读取 API Key 的环境变量名称
 */
public record OpenAiCompatibleProviderConfig(
        String id, String name, URI baseUrl, String apiKeyEnvironmentVariable) {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern ENVIRONMENT_VARIABLE =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public OpenAiCompatibleProviderConfig {
        id = requireMatching(id, "id", ID);
        name = requireText(name, "name");
        baseUrl = requireHttpUrl(baseUrl);
        apiKeyEnvironmentVariable = requireMatching(
                apiKeyEnvironmentVariable,
                "apiKeyEnvironmentVariable",
                ENVIRONMENT_VARIABLE);
    }

    /** 创建模型提供方 Builder；默认显示名为 ID，API Key 环境变量为 {@code OPENAI_API_KEY}。 */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** 用于构建不可变的 OpenAI-compatible 模型提供方配置。 */
    public static final class Builder {
        private final String id;
        private String name;
        private URI baseUrl;
        private String apiKeyEnvironmentVariable = "OPENAI_API_KEY";

        private Builder(String id) {
            this.id = requireMatching(id, "id", ID);
            name = id;
        }

        /** 设置便于展示的提供方名称。 */
        public Builder name(String value) {
            name = requireText(value, "value");
            return this;
        }

        /** 设置 OpenAI-compatible API 的基础地址。 */
        public Builder baseUrl(URI value) {
            baseUrl = Objects.requireNonNull(value, "value");
            return this;
        }

        /** 设置 Codex 应读取 API Key 的环境变量名称。 */
        public Builder apiKeyEnvironmentVariable(String value) {
            apiKeyEnvironmentVariable = requireMatching(
                    value, "value", ENVIRONMENT_VARIABLE);
            return this;
        }

        /** 创建配置。 */
        public OpenAiCompatibleProviderConfig build() {
            return new OpenAiCompatibleProviderConfig(
                    id, name, Objects.requireNonNull(baseUrl, "baseUrl"), apiKeyEnvironmentVariable);
        }
    }

    private static URI requireHttpUrl(URI value) {
        Objects.requireNonNull(value, "baseUrl");
        if (!value.isAbsolute()
                || !(value.getScheme().equalsIgnoreCase("http")
                        || value.getScheme().equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("baseUrl must be an absolute HTTP(S) URI");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private static String requireMatching(String value, String name, Pattern pattern) {
        requireText(value, name);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " has an invalid format");
        }
        return value;
    }
}
