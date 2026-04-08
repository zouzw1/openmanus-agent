package com.openmanus.saa.service.context;

import com.openmanus.saa.model.context.ConversationContext;
import com.openmanus.saa.model.session.Session;
import com.openmanus.saa.service.session.SessionMemoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationContextFactoryTest {

    @Mock
    private SessionMemoryService sessionMemoryService;

    @Test
    void create_shouldBuildContextWithHistory() {
        Session session = new Session("test-session");
        when(sessionMemoryService.getOrCreate("test-session")).thenReturn(session);
        when(sessionMemoryService.summarizeHistory(any(), anyInt())).thenReturn("previous conversation");

        ConversationContextFactory factory = new ConversationContextFactory(sessionMemoryService);
        ConversationContext ctx = factory.create("test-session", "what's the weather?");

        assertEquals("test-session", ctx.sessionId());
        assertEquals("what's the weather?", ctx.currentPrompt());
        assertEquals("previous conversation", ctx.conversationHistory());
    }

    @Test
    void create_withCustomHistoryLimit_shouldUseLimit() {
        Session session = new Session("test-session");
        when(sessionMemoryService.getOrCreate("test-session")).thenReturn(session);
        when(sessionMemoryService.summarizeHistory(any(), eq(5))).thenReturn("short history");

        ConversationContextFactory factory = new ConversationContextFactory(sessionMemoryService);
        ConversationContext ctx = factory.create("test-session", "prompt", 5);

        assertEquals("short history", ctx.conversationHistory());
        verify(sessionMemoryService).summarizeHistory(any(), eq(5));
    }
}