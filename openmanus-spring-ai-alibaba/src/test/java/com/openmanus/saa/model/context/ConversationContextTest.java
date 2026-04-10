package com.openmanus.saa.model.context;

import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.session.Session;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class ConversationContextTest {

    @Test
    void from_shouldExtractPreferences() {
        Session session = new Session("test-session");
        session = session.putMemory("userPreferences", Map.of("diet", "喜欢吃辣"));

        ConversationContext ctx = ConversationContext.from(session, "new prompt", "history");

        assertEquals("test-session", ctx.sessionId());
        assertEquals("new prompt", ctx.currentPrompt());
        assertTrue(ctx.hasUserPreferences());
        assertFalse(ctx.hasPausedWorkflow());
    }

    @Test
    void from_emptySession_shouldReturnDefaults() {
        Session session = new Session("test-session");
        ConversationContext ctx = ConversationContext.from(session, "prompt", null);

        assertFalse(ctx.hasUserPreferences());
        assertFalse(ctx.hasPausedWorkflow());
    }

    @Test
    void getPreference_shouldReturnValueWithType() {
        Session session = new Session("test-session");
        session = session.putMemory("userPreferences", Map.of("budget", 500));
        ConversationContext ctx = ConversationContext.from(session, "prompt", null);

        Integer budget = ctx.getPreference("budget", Integer.class, 0);
        assertEquals(500, budget);
    }

    @Test
    void from_withInterruptedWorkflow_shouldSetFlags() {
        Session session = new Session("test-session");
        HumanFeedbackRequest feedback = new HumanFeedbackRequest(
            "test-session", "旅行计划", "plan-123", 2, null, null, "缺少参数", "补充参数"
        );
        ConversationContext ctx = ConversationContext.from(
            session, "prompt", "history",
            true, Optional.of(feedback)
        );

        assertTrue(ctx.hasPausedWorkflow());
        assertTrue(ctx.pendingFeedback().isPresent());
        assertEquals(2, ctx.pendingFeedback().get().getStepIndex());
    }

    @Test
    void from_withoutInterruptedWorkflow_shouldHaveEmptyPendingFeedback() {
        Session session = new Session("test-session");
        ConversationContext ctx = ConversationContext.from(
            session, "prompt", "history",
            false, Optional.empty()
        );

        assertFalse(ctx.hasPausedWorkflow());
        assertFalse(ctx.pendingFeedback().isPresent());
    }
}
