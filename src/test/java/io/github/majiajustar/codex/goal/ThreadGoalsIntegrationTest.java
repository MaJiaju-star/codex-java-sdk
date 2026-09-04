package io.github.majiajustar.codex.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.majiajustar.codex.CodexClient;
import io.github.majiajustar.codex.CodexClientConfig;
import io.github.majiajustar.codex.MockAppServer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThreadGoalsIntegrationTest {
    @Test
    void managesPersistentThreadGoal() {
        try (var codex = CodexClient.create(config())) {
            var goals = codex.startThread().goals();
            var created = goals.set(GoalUpdate.builder()
                    .objective("Ship the Java SDK")
                    .status(ThreadGoalStatus.ACTIVE)
                    .tokenBudget(1000)
                    .build());

            assertEquals("Ship the Java SDK", created.objective());
            assertEquals(ThreadGoalStatus.ACTIVE, created.status());
            assertEquals(1000L, created.tokenBudget());
            assertEquals(created.objective(), goals.get().orElseThrow().objective());
            assertTrue(goals.clear());
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
