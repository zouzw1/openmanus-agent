package com.openmanus.saa.model.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmanus.saa.model.InferencePolicy;
import com.openmanus.saa.model.ResponseMode;

import java.time.Instant;
import java.util.*;

/**
 * 统一会话模型（不可变 Record）。
 * 替换旧的 SessionState 和 ConversationSession，提供消息管理、工作记忆、执行日志和 Token 统计。
 */
public record Session(
    int version,
    String sessionId,
    Instant createdAt,
    Instant updatedAt,
    Instant lastAccessedAt,
    List<ConversationMessage> messages,
    Map<String, Object> workingMemory,
    List<String> executionLog,
    TokenUsage cumulativeUsage,
    InferencePolicy latestInferencePolicy,
    ResponseMode latestResponseMode
) {
    @JsonCreator
    public Session(
        @JsonProperty("version") int version,
        @JsonProperty("sessionId") String sessionId,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt,
        @JsonProperty("lastAccessedAt") Instant lastAccessedAt,
        @JsonProperty("messages") List<ConversationMessage> messages,
        @JsonProperty("workingMemory") Map<String, Object> workingMemory,
        @JsonProperty("executionLog") List<String> executionLog,
        @JsonProperty("cumulativeUsage") TokenUsage cumulativeUsage,
        @JsonProperty("latestInferencePolicy") InferencePolicy latestInferencePolicy,
        @JsonProperty("latestResponseMode") ResponseMode latestResponseMode
    ) {
        this.version = version > 0 ? version : 1;
        this.sessionId = Objects.requireNonNull(sessionId);
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.lastAccessedAt = lastAccessedAt != null ? lastAccessedAt : this.createdAt;
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.workingMemory = workingMemory != null ? new HashMap<>(workingMemory) : new HashMap<>();
        this.executionLog = executionLog != null ? new ArrayList<>(executionLog) : new ArrayList<>();
        this.cumulativeUsage = cumulativeUsage != null ? cumulativeUsage : TokenUsage.zero();
        this.latestInferencePolicy = latestInferencePolicy;
        this.latestResponseMode = latestResponseMode;
    }

    public Session(String sessionId) {
        this(1, sessionId, Instant.now(), Instant.now(), Instant.now(),
             new ArrayList<>(), new HashMap<>(), new ArrayList<>(), TokenUsage.zero(),
             null, null);
    }

    // ========== 消息操作 ==========

    public Session addMessage(ConversationMessage message) {
        List<ConversationMessage> newMessages = new ArrayList<>(this.messages);
        newMessages.add(message);
        return new Session(version, sessionId, createdAt, Instant.now(), Instant.now(),
                          newMessages, workingMemory, executionLog, cumulativeUsage,
                          latestInferencePolicy, latestResponseMode);
    }

    public Session addSystemMessage(String content) {
        return addMessage(ConversationMessage.system(content));
    }

    public Session addUserMessage(String content) {
        return addMessage(ConversationMessage.userText(content));
    }

    public Session addAssistantMessage(String content) {
        return addMessage(ConversationMessage.assistant(List.of(new TextBlock(content))));
    }

    public Session recordToolCall(String toolName, String input) {
        String toolId = "tool-" + UUID.randomUUID();
        ToolUseBlock toolUse = new ToolUseBlock(toolId, toolName, input);
        ConversationMessage msg = new ConversationMessage(
            MessageRole.ASSISTANT, List.of(toolUse), null, Instant.now()
        );
        return addMessage(msg);
    }

    public Session recordToolResult(String toolUseId, String toolName, String output, boolean isError) {
        return addMessage(ConversationMessage.toolResult(toolUseId, toolName, output, isError));
    }

    // ========== 工作记忆 ==========

    public Session putMemory(String key, Object value) {
        Map<String, Object> newMemory = new HashMap<>(this.workingMemory);
        newMemory.put(key, value);
        return new Session(version, sessionId, createdAt, Instant.now(), Instant.now(),
                          messages, newMemory, executionLog, cumulativeUsage,
                          latestInferencePolicy, latestResponseMode);
    }

    public Optional<Object> getMemory(String key) {
        return Optional.ofNullable(workingMemory.get(key));
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getMemory(String key, Class<T> type) {
        return getMemory(key).filter(type::isInstance).map(type::cast);
    }

    public Session removeMemory(String key) {
        Map<String, Object> newMemory = new HashMap<>(this.workingMemory);
        newMemory.remove(key);
        return new Session(version, sessionId, createdAt, Instant.now(), Instant.now(),
                          messages, newMemory, executionLog, cumulativeUsage,
                          latestInferencePolicy, latestResponseMode);
    }

    // ========== 执行日志 ==========

    public Session addExecutionLog(String content) {
        List<String> newLog = new ArrayList<>(this.executionLog);
        newLog.add(content);
        return new Session(version, sessionId, createdAt, Instant.now(), Instant.now(),
                          messages, workingMemory, newLog, cumulativeUsage,
                          latestInferencePolicy, latestResponseMode);
    }

    // ========== Token 统计 ==========

    public Session updateCumulativeUsage(TokenUsage usage) {
        return new Session(version, sessionId, createdAt, Instant.now(), Instant.now(),
                          messages, workingMemory, executionLog, cumulativeUsage.add(usage),
                          latestInferencePolicy, latestResponseMode);
    }

    // ========== 便捷查询 ==========

    @JsonIgnore
    public boolean isEmpty() {
        return messages.isEmpty() && workingMemory.isEmpty() && executionLog.isEmpty();
    }

    public Session clear() {
        return new Session(version, sessionId, createdAt, Instant.now(), Instant.now(),
                          new ArrayList<>(), new HashMap<>(), new ArrayList<>(), TokenUsage.zero(),
                          null, null);
    }

    @JsonIgnore
    public int estimateTokens() {
        int total = 0;
        for (ConversationMessage msg : messages) {
            for (ContentBlock block : msg.blocks()) {
                if (block instanceof TextBlock t) {
                    total += t.text() != null ? t.text().length() / 4 + 1 : 0;
                } else if (block instanceof ToolUseBlock tu) {
                    total += (tu.name().length() + tu.input().length()) / 4 + 1;
                } else if (block instanceof ToolResultBlock tr) {
                    total += (tr.toolName().length() + tr.output().length()) / 4 + 1;
                }
            }
        }
        return total;
    }

    public Session withMessages(List<ConversationMessage> newMessages) {
        return new Session(version, sessionId, createdAt, Instant.now(), Instant.now(),
                          new ArrayList<>(newMessages), workingMemory, executionLog, cumulativeUsage,
                          latestInferencePolicy, latestResponseMode);
    }

    @JsonIgnore
    public Map<String, Object> getAllMemory() {
        return Map.copyOf(workingMemory);
    }

    @JsonIgnore
    public List<String> getExecutionLogList() {
        return List.copyOf(executionLog);
    }
}
