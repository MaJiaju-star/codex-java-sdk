package io.github.majiajustar.codex.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        var lifecycle =
                assertInstanceOf(CodexNotification.ThreadLifecycle.class, archived.notification());
        assertEquals(CodexEventType.THREAD_ARCHIVED, lifecycle.type());
        assertEquals("thread/archived", lifecycle.method());
        assertEquals("thread-1", lifecycle.threadId());

        var skills = new CodexEvent("skills/changed", JsonSupport.MAPPER.createObjectNode());
        assertEquals(CodexEventType.SKILLS_CHANGED, skills.type());
        assertInstanceOf(CodexNotification.SkillsChanged.class, skills.notification());

        var unknown = new CodexEvent("future/notification", JsonSupport.MAPPER.createObjectNode())
                .notification();
        assertEquals(CodexEventType.UNKNOWN, unknown.type());
        assertEquals("future/notification", unknown.method());
    }

    @Test
    void preservesTypedDeltaIndexesAndErrors() throws Exception {
        var reasoning = notification("item/reasoning/textDelta", """
                {
                  "threadId":"thread-1",
                  "turnId":"turn-1",
                  "itemId":"item-1",
                  "contentIndex":2,
                  "delta":"details"
                }
                """);
        var reasoningDelta = assertInstanceOf(CodexNotification.Delta.class, reasoning);
        assertEquals(CodexEventType.REASONING_TEXT_DELTA, reasoningDelta.type());
        assertEquals("item/reasoning/textDelta", reasoningDelta.method());
        assertEquals(2L, reasoningDelta.contentIndex());
        assertNull(reasoningDelta.summaryIndex());

        var error = notification("error", """
                {
                  "threadId":"thread-1",
                  "turnId":"turn-1",
                  "willRetry":false,
                  "error":{
                    "message":"Provider rejected the request",
                    "additionalDetails":"status=401"
                  }
                }
                """);
        var typedError = assertInstanceOf(CodexNotification.Error.class, error);
        assertEquals(CodexEventType.ERROR, typedError.type());
        assertEquals("Provider rejected the request", typedError.error().message());
        assertEquals("status=401", typedError.error().additionalDetails());
    }

    @Test
    void parsesStructuredFileChangeKinds() throws Exception {
        var notification = notification("item/fileChange/patchUpdated", """
                {
                  "threadId":"thread-1",
                  "turnId":"turn-1",
                  "itemId":"item-1",
                  "changes":[{
                    "path":"src/New.java",
                    "kind":{"type":"update","move_path":"src/Old.java"},
                    "diff":"@@ -1 +1 @@"
                  }]
                }
                """);
        var fileChange =
                assertInstanceOf(CodexNotification.FileChangePatchUpdated.class, notification);
        assertEquals(
                new CodexItem.PatchChangeKind.Update("src/Old.java"),
                fileChange.changes().getFirst().kind());
    }

    private static CodexNotification notification(String method, String json) throws Exception {
        return new CodexEvent(method, JsonSupport.MAPPER.readTree(json)).notification();
    }
}
