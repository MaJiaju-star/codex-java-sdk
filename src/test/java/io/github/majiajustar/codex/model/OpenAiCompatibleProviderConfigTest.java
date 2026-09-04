package io.github.majiajustar.codex.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.majiajustar.codex.CodexClientConfig;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleProviderConfigTest {
    @Test
    void registersAndSelectsProviderWithoutPuttingApiKeyOnCommandLine() {
        var provider = OpenAiCompatibleProviderConfig.builder("internal")
                .name("Internal Codex")
                .baseUrl(URI.create("https://codex-api.internal/v1"))
                .build();
        var config = CodexClientConfig.builder()
                .command(List.of("codex", "app-server"))
                .configOverride("model_provider=\"old\"")
                .openAiCompatibleProvider(provider)
                .apiKey("internal-secret")
                .build();

        assertEquals(
                List.of(
                        "codex",
                        "--config",
                        "model_provider=\"old\"",
                        "--config",
                        "model_provider=\"internal\"",
                        "--config",
                        "model_providers.internal={name=\"Internal Codex\","
                                + "base_url=\"https://codex-api.internal/v1\","
                                + "env_key=\"OPENAI_API_KEY\",wire_api=\"responses\","
                                + "requires_openai_auth=false}",
                        "app-server"),
                config.command());
        assertEquals("internal-secret", config.environment().get("OPENAI_API_KEY"));
        assertFalse(String.join(" ", config.command()).contains("internal-secret"));
    }

    @Test
    void supportsCustomApiKeyEnvironmentVariable() {
        var provider = OpenAiCompatibleProviderConfig.builder("corp")
                .baseUrl(URI.create("http://localhost:8080/v1"))
                .apiKeyEnvironmentVariable("INTERNAL_CODEX_API_KEY")
                .build();
        var config = CodexClientConfig.builder()
                .command(List.of("codex", "app-server"))
                .environment("INTERNAL_CODEX_API_KEY", "secret")
                .openAiCompatibleProvider(provider)
                .build();

        assertEquals("secret", config.environment().get("INTERNAL_CODEX_API_KEY"));
        assertEquals(
                "model_providers.corp={name=\"corp\",base_url=\"http://localhost:8080/v1\","
                        + "env_key=\"INTERNAL_CODEX_API_KEY\",wire_api=\"responses\","
                        + "requires_openai_auth=false}",
                config.command().get(config.command().size() - 2));
    }

    @Test
    void validatesProviderConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OpenAiCompatibleProviderConfig.builder("invalid.name"));
        assertThrows(
                IllegalArgumentException.class,
                () -> OpenAiCompatibleProviderConfig.builder("internal")
                        .baseUrl(URI.create("file:///tmp/api"))
                        .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> OpenAiCompatibleProviderConfig.builder("internal")
                        .baseUrl(URI.create("https://codex-api.internal/v1"))
                        .apiKeyEnvironmentVariable("INVALID-NAME"));
    }
}
