package com.openmanus.saa.model.session;

import com.openmanus.saa.model.InferencePolicy;
import com.openmanus.saa.model.ResponseMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SessionState {

    private final String sessionId;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant lastAccessedAt;
    private final List<ConversationMessage> messages = new ArrayList<>();
    private final List<String> executionLog = new ArrayList<>();
    private InferencePolicy latestInferencePolicy;
    private ResponseMode latestResponseMode;

    // 压缩摘要
    private String compactedSummary;

    public SessionState(String sessionId) {
        this.sessionId = sessionId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.lastAccessedAt = this.createdAt;
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

    /**
     * 获取最后访问时间（用于 TTL）
     */
    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    /**
     * 更新最后访问时间
     */
    public void touch() {
        this.lastAccessedAt = Instant.now();
    }

    public List<ConversationMessage> getMessages() {
        return messages;
    }

    public List<String> getExecutionLog() {
        return executionLog;
    }

    public InferencePolicy getLatestInferencePolicy() {
        return latestInferencePolicy;
    }

    public ResponseMode getLatestResponseMode() {
        return latestResponseMode;
    }

    public void addMessage(String role, String content) {
        MessageRole msgRole = MessageRole.valueOf(role.toUpperCase());
        messages.add(new ConversationMessage(msgRole, List.of(new TextBlock(content)), null, Instant.now()));
        updatedAt = Instant.now();
    }

    public void addExecutionLog(String content) {
        executionLog.add(content);
        updatedAt = Instant.now();
    }

    public void setLatestInferencePolicy(InferencePolicy latestInferencePolicy) {
        this.latestInferencePolicy = latestInferencePolicy;
        updatedAt = Instant.now();
    }

    public void setLatestResponseMode(ResponseMode latestResponseMode) {
        this.latestResponseMode = latestResponseMode;
        updatedAt = Instant.now();
    }

    /**
     * 获取压缩摘要
     */
    public Optional<String> getCompactedSummary() {
        return Optional.ofNullable(compactedSummary);
    }

    /**
     * 设置压缩摘要
     */
    public void setCompactedSummary(String summary) {
        this.compactedSummary = summary;
        updatedAt = Instant.now();
    }

    /**
     * 添加消息对象
     */
    public void addMessage(ConversationMessage message) {
        messages.add(message);
        updatedAt = Instant.now();
    }

    /**
     * 获取消息数量
     */
    public int getMessageCount() {
        return messages.size();
    }

    /**
     * 估算Token数量（简单估算：每4字符约1 token）
     */
    public int estimateTokens() {
        return messages.stream()
            .mapToInt(m -> m.blocks().stream()
                .mapToInt(b -> b.asText().length() / 4 + 1)
                .sum())
            .sum();
    }
}
