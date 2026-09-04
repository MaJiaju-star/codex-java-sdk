package io.github.majiajustar.codex.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.majiajustar.codex.CodexClient;
import io.github.majiajustar.codex.CodexClientConfig;
import io.github.majiajustar.codex.MockAppServer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillsClientIntegrationTest {
    @Test
    void discoversAndConfiguresSkills() {
        try (var codex = CodexClient.create(config())) {
            var result = codex.skills().list(new SkillsListOptions(List.of(Path.of("/workspace")), true));
            var entry = result.data().getFirst();
            var skill = entry.skills().getFirst();

            assertEquals(Path.of("/workspace"), entry.workingDirectory());
            assertEquals("java-review", skill.name());
            assertEquals(SkillScope.REPO, skill.scope());
            assertEquals("Java Review", skill.interfaceInfo().displayName());
            assertEquals("mvn", skill.dependencies().tools().getFirst().command());

            codex.skills().setExtraRoots(List.of(Path.of("/company/skills")));
            assertTrue(codex.skills().enableByName("java-review"));
            assertFalse(codex.skills().disable(skill.path()));
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
