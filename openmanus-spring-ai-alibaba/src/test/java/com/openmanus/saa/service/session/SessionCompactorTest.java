package com.openmanus.saa.service.session;

import com.openmanus.saa.model.session.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SessionCompactorTest {

    private SessionCompactor compactor;

    @BeforeEach
    void setUp() {
        compactor = new SessionCompactor();
    }

    @Test
    void shouldNotCompactSmallSession() {
        Session session = new Session("test");
        session = session.addMessage(ConversationMessage.userText("hello"));

        CompactionConfig config = new CompactionConfig(4, 100);
        boolean shouldCompact = compactor.shouldCompact(session, config);

        assertThat(shouldCompact).isFalse();
    }

    @Test
    void shouldCompactLargeSession() {
        Session session = new Session("test");
        for (int i = 0; i < 10; i++) {
            session = session.addMessage(ConversationMessage.userText("x".repeat(100)));
        }

        CompactionConfig config = new CompactionConfig(2, 10);
        boolean shouldCompact = compactor.shouldCompact(session, config);

        assertThat(shouldCompact).isTrue();
    }

    @Test
    void compactSessionPreservesRecentMessages() {
        Session session = new Session("test");
        session = session.addMessage(ConversationMessage.userText("old message 1"));
        session = session.addMessage(ConversationMessage.userText("old message 2"));
        session = session.addMessage(ConversationMessage.userText("recent message 1"));
        session = session.addMessage(ConversationMessage.userText("recent message 2"));

        CompactionConfig config = new CompactionConfig(2, 1);
        CompactionResult result = compactor.compactSession(session, config);

        assertThat(result.removedMessageCount()).isEqualTo(2);
        assertThat(result.compactedSession().messages()).hasSize(3); // 1 system + 2 recent
        assertThat(result.compactedSession().messages().get(0).role()).isEqualTo(MessageRole.SYSTEM);
    }

    @Test
    void compactSessionGeneratesSummary() {
        Session session = new Session("test");
        session = session.addMessage(ConversationMessage.userText("Update rust/crates/runtime/src/compact.rs"));
        session = session.addMessage(ConversationMessage.userText("Add tests for compaction"));

        CompactionConfig config = new CompactionConfig(1, 1);
        CompactionResult result = compactor.compactSession(session, config);

        assertThat(result.summary()).isNotEmpty();
        assertThat(result.summary()).contains("Scope:");
        assertThat(result.summary()).contains("Key timeline:");
    }

    @Test
    void formatCompactSummaryRemovesAnalysisTag() {
        String summary = "<analysis>scratch</analysis>\n<summary>Kept work</summary>";

        String formatted = compactor.formatCompactSummary(summary);

        assertThat(formatted).doesNotContain("<analysis>");
        assertThat(formatted).contains("Summary:");
    }

    @Test
    void getCompactContinuationMessageIncludesPreamble() {
        String summary = "Test summary";

        String continuation = compactor.getCompactContinuationMessage(summary, true, true);

        assertThat(continuation).contains("This session is being continued");
        assertThat(continuation).contains("Test summary");
        assertThat(continuation).contains("Recent messages are preserved");
    }

    @Test
    void estimateSessionTokensCalculatesTotal() {
        Session session = new Session("test");
        session = session.addMessage(ConversationMessage.userText("Hello World"));
        session = session.addMessage(ConversationMessage.userText("Test message"));

        int tokens = compactor.estimateSessionTokens(session);

        assertThat(tokens).isGreaterThan(0);
    }
}