package com.openmanus.saa.model.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SessionState {

    private final String sessionId;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<ConversationMessage> messages = new ArrayList<>();
    private final List<String> executionLog = new ArrayList<>();

    public SessionState(String sessionId) {
        this.sessionId = sessionId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ConversationMessage> getMessages() {
        return messages;
    }

    public List<String> getExecutionLog() {
        return executionLog;
    }

    public void addMessage(String role, String content) {
        messages.add(new ConversationMessage(role, content, Instant.now()));
        updatedAt = Instant.now();
    }

    public void addExecutionLog(String content) {
        executionLog.add(content);
        updatedAt = Instant.now();
    }
}
