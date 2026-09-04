package io.github.majiajustar.codex.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.majiajustar.codex.goal.ThreadGoalStatus;
import io.github.majiajustar.codex.internal.JsonSupport;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AppServerNotificationTest {
    @Test
    void parsesGoalPlanAndConfigurationNotifications() throws Exception {
        var goal = notification("thread/goal/updated", """
                {
                  "threadId":"thread-1",
                  "turnId":"turn-1",
                  "goal":{
                    "threadId":"thread-1",
                    "objective":"Ship SDK",
                    "status":"active",
                    "tokenBudget":1000,
                    "tokensUsed":20,
                    "timeUsedSeconds":3,
                    "createdAt":1,
                    "updatedAt":2
                  }
                }
                """);
        var goalUpdated = assertInstanceOf(CodexNotification.ThreadGoalUpdated.class, goal);
        assertEquals(ThreadGoalStatus.ACTIVE, goalUpdated.goal().status());

        var plan = notification("turn/plan/updated", """
                {
                  "threadId":"thread-1",
                  "turnId":"turn-1",
                  "explanation":"Implement in stages",
                  "plan":[{"step":"Add API","status":"inProgress"}]
                }
                """);
        var planUpdated = assertInstanceOf(CodexNotification.TurnPlanUpdated.class, plan);
        assertEquals(CodexNotification.PlanStepStatus.IN_PROGRESS, planUpdated.plan().getFirst().status());

        var warning = notification("configWarning", """
                {
                  "summary":"Invalid setting",
                  "details":"Use a supported value",
                  "path":"/workspace/.codex/config.toml",
                  "range":{"start":{"line":2,"column":1},"end":{"line":2,"column":8}}
                }
                """);
        var configWarning = assertInstanceOf(CodexNotification.ConfigWarning.class, warning);
        assertEquals(Path.of("/workspace/.codex/config.toml"), configWarning.path());
        assertEquals(new CodexNotification.TextPosition(2, 1), configWarning.range().start());
    }

    @Test
    void classifiesThreadLifecycleAndInvalidationNotifications() throws Exception {
        var archived = new CodexEvent(
                "thread/archived", JsonSupport.MAPPER.readTree("{\"threadId\":\"thread-1\"}"));
        assertEquals(CodexEventType.THREAD_ARCHIVED, archived.type());
        assertEquals(
                "thread-1",
                assertInstanceOf(CodexNotification.ThreadLifecycle.class, archived.notification())
                        .threadId());

        var skills = new CodexEvent("skills/changed", JsonSupport.MAPPER.createObjectNode());
        assertEquals(CodexEventType.SKILLS_CHANGED, skills.type());
        assertInstanceOf(CodexNotification.SkillsChanged.class, skills.notification());
    }

    private static CodexNotification notification(String method, String json) throws Exception {
        return new CodexEvent(method, JsonSupport.MAPPER.readTree(json)).notification();
    }
}
