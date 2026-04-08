package com.openmanus.saa.model.context;

import com.openmanus.saa.model.session.Session;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConversationContextTest {

    @Test
    void from_shouldExtractPreferencesAndWorkflowState() {
        Session session = new Session("test-session");
        session = session.putMemory("userPreferences", Map.of("diet", "喜欢吃辣"));
        session = session.putMemory("workflowState", WorkflowState.completed("test", "plan-1", "result"));

        ConversationContext ctx = ConversationContext.from(session, "new prompt", "history");

        assertEquals("test-session", ctx.sessionId());
        assertEquals("new prompt", ctx.currentPrompt());
        assertTrue(ctx.hasUserPreferences());
        assertTrue(ctx.workflowState().isCompleted());
    }

    @Test
    void from_emptySession_shouldReturnDefaults() {
        Session session = new Session("test-session");
        ConversationContext ctx = ConversationContext.from(session, "prompt", null);

        assertFalse(ctx.hasUserPreferences());
        assertFalse(ctx.hasPausedWorkflow());
        assertEquals(WorkflowState.WorkflowStatus.NONE, ctx.workflowState().status());
    }

    @Test
    void getPreference_shouldReturnValueWithType() {
        Session session = new Session("test-session");
        session = session.putMemory("userPreferences", Map.of("budget", 500));
        ConversationContext ctx = ConversationContext.from(session, "prompt", null);

        Integer budget = ctx.getPreference("budget", Integer.class, 0);
        assertEquals(500, budget);
    }
}