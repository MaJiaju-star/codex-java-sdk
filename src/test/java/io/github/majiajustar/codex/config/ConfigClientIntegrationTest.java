package io.github.majiajustar.codex.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.majiajustar.codex.CodexClient;
import io.github.majiajustar.codex.CodexClientConfig;
import io.github.majiajustar.codex.MockAppServer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigClientIntegrationTest {
    @Test
    void readsWritesAndChecksManagedRequirements() {
        try (var codex = CodexClient.create(config())) {
            var snapshot = codex.config().read(new ConfigReadOptions(Path.of("/workspace"), true));
            assertEquals("gpt-test", snapshot.config().path("model").asText());
            assertEquals(ConfigLayerType.USER, snapshot.origins().get("model").type());
            assertEquals(1, snapshot.layers().size());

            var write = codex.config().write(ConfigWriteRequest.builder("model", "gpt-new")
                    .expectedVersion("v1")
                    .build());
            assertEquals(ConfigWriteResult.WriteStatus.OK, write.status());
            assertEquals("v2", write.version());

            var batch = codex.config().batchWrite(ConfigBatchWriteRequest.builder()
                    .replace("model", "gpt-new")
                    .replace("web_search", "cached")
                    .expectedVersion("v1")
                    .reloadUserConfig(true)
                    .build());
            assertEquals(ConfigWriteResult.WriteStatus.OK, batch.status());

            var requirements = codex.config().requirements();
            assertEquals(Set.of("workspace-write"), requirements.allowedSandboxModes().orElseThrow());
            assertEquals(
                    Set.of("disabled", "cached"),
                    requirements.allowedWebSearchModes().orElseThrow());
            assertFalse(requirements.allowBrowserAndComputerUse().orElseThrow());
            assertEquals(
                    "Use internal services only",
                    requirements.additionalDeveloperInstructions().orElseThrow());
        }
    }

    private static CodexClientConfig config() {
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return CodexClientConfig.builder()
                .command(List.of(
                        java, "-cp", System.getProperty("java.class.path"), MockAppServer.class.getName()))
                .requestTimeout(Duration.ofSeconds(10))
                .build();
    }
}
