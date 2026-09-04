package io.github.majiajustar.codex.internal;

import io.github.majiajustar.codex.model.OpenAiCompatibleProviderConfig;
import java.util.Objects;

/** 将强类型模型提供方配置编码为 Codex CLI 接受的 TOML 配置覆盖。 */
public final class ModelProviderConfigOverrideSerializer {
    private ModelProviderConfigOverrideSerializer() {}

    /** 序列化一个 OpenAI-compatible 模型提供方。 */
    public static String serialize(OpenAiCompatibleProviderConfig config) {
        Objects.requireNonNull(config, "config");
        return "model_providers."
                + config.id()
                + "={name="
                + string(config.name())
                + ",base_url="
                + string(config.baseUrl().toString())
                + ",env_key="
                + string(config.apiKeyEnvironmentVariable())
                + ",wire_api=\"responses\",requires_openai_auth=false}";
    }

    private static String string(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
