package com.openmanus.saa.model.session;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;

/**
 * 会话级别的上下文管理。
 * 为Workflow执行提供连续的对话历史和状态存储。
 *
 * <p>对比Claw的Session设计：
 * <ul>
 *   <li>持续累积消息，支持多轮对话</li>
 *   <li>Tool结果作为结构化ContentBlock存储</li>
 *   <li>提供工作内存用于跨Step状态共享</li>
 * </ul>
 */
public class ConversationSession {

    private static final Logger log = LoggerFactory.getLogger(ConversationSession.class);

    private final String sessionId;
    private final SessionMessages messages;
    private final Map<String, Object> workingMemory;
    private final Instant createdAt;
    private Instant lastAccessedAt;

    // 配置
    private final int maxMessages;
    private final int maxChars;

    public ConversationSession(String sessionId) {
        this(sessionId, 50, 32000);
    }

    public ConversationSession(String sessionId, int maxMessages, int maxChars) {
        this.sessionId = sessionId;
        this.maxMessages = maxMessages;
        this.maxChars = maxChars;
        this.messages = new SessionMessages(maxMessages, maxChars);
        this.workingMemory = new HashMap<>();
        this.createdAt = Instant.now();
        this.lastAccessedAt = this.createdAt;
    }

    // ================== 消息记录方法 ==================

    /**
     * 记录Step开始。
     */
    public void recordStepStart(String stepDescription) {
        touch();
        messages.addUserMessage(stepDescription);
        log.debug("Session {} recorded step start: {}", sessionId, truncateForLog(stepDescription));
    }

    /**
     * 记录Step结果。
     */
    public void recordStepResult(String result) {
        touch();
        messages.addAssistantMessage(result);
        log.debug("Session {} recorded step result: {}", sessionId, truncateForLog(result));
    }

    /**
     * 记录Tool调用。
     *
     * @return toolId 用于后续匹配Tool结果
     */
    public String recordToolCall(String toolName, String input) {
        touch();
        return messages.recordToolUse(toolName, input);
    }

    /**
     * 记录Tool结果。
     */
    public void recordToolResult(String toolId, String toolName, String output, boolean isError) {
        touch();
        messages.recordToolResult(toolId, toolName, output, isError);
    }

    /**
     * 添加系统消息。
     */
    public void addSystemMessage(String content) {
        touch();
        messages.addSystemMessage(content);
    }

    // ================== 上下文构建方法 ==================

    /**
     * 构建用于LLM调用的消息历史。
     */
    public List<Message> buildContextForLLM() {
        touch();
        return messages.toSpringAIMessages();
    }

    /**
     * 获取消息摘要（用于调试和多Agent上下文共享）。
     * 返回简化版的消息历史摘要，用于多Agent场景的上下文传递。
     */
    public String getMessageSummary() {
        return messages.buildContextSummary();
    }

    /**
     * 获取消息数量。
     */
    public int getMessageCount() {
        return messages.size();
    }

    // ================== 工作内存方法 ==================

    /**
     * 存储工作内存。
     */
    public void putMemory(String key, Object value) {
        touch();
        workingMemory.put(key, value);
    }

    /**
     * 获取工作内存。
     */
    public Optional<Object> getMemory(String key) {
        touch();
        return Optional.ofNullable(workingMemory.get(key));
    }

    /**
     * 获取类型化的工作内存。
     */
    public <T> Optional<T> getMemory(String key, Class<T> type) {
        return getMemory(key).filter(type::isInstance).map(type::cast);
    }

    /**
     * 移除工作内存。
     */
    public void removeMemory(String key) {
        touch();
        workingMemory.remove(key);
    }

    /**
     * 清空工作内存。
     */
    public void clearMemory() {
        touch();
        workingMemory.clear();
    }

    /**
     * 获取所有工作内存。
     */
    public Map<String, Object> getAllMemory() {
        touch();
        return Map.copyOf(workingMemory);
    }

    // ================== 多Agent任务追踪方法 ==================

    /**
     * 记录Agent任务执行结果。
     * 用于多Agent场景下追踪各个Agent的执行情况。
     *
     * @param peerId Agent标识
     * @param task 任务信息
     * @param result 执行结果
     */
    public void recordAgentTask(String peerId, Object task, Object result) {
        touch();
        String content = String.format(
            "[Agent %s] Task completed\nResult: %s",
            peerId,
            result != null ? truncate(result.toString(), 200) : "null"
        );
        messages.addSystemMessage(content);
        log.debug("Recorded agent task for {} in session {}", peerId, sessionId);
    }

    /**
     * 记录Agent任务开始。
     *
     * @param peerId Agent标识
     * @param taskDescription 任务描述
     */
    public void recordAgentTaskStart(String peerId, String taskDescription) {
        touch();
        String content = String.format(
            "[Agent %s] Starting task: %s",
            peerId,
            truncate(taskDescription, 100)
        );
        messages.addSystemMessage(content);
    }

    /**
     * 记录Agent任务失败。
     *
     * @param peerId Agent标识
     * @param error 错误信息
     */
    public void recordAgentTaskError(String peerId, String error) {
        touch();
        String content = String.format(
            "[Agent %s] Task failed: %s",
            peerId,
            truncate(error, 200)
        );
        messages.addSystemMessage(content);
    }

    // ================== 状态方法 ==================

    /**
     * 获取会话ID。
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取创建时间。
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取最后访问时间。
     */
    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    /**
     * 检查会话是否为空。
     */
    public boolean isEmpty() {
        return messages.isEmpty() && workingMemory.isEmpty();
    }

    /**
     * 清空会话。
     */
    public void clear() {
        messages.clear();
        workingMemory.clear();
        log.info("Session {} cleared", sessionId);
    }

    // ================== 私有方法 ==================

    private void touch() {
        this.lastAccessedAt = Instant.now();
    }

    private String truncateForLog(String text) {
        if (text == null) return "null";
        if (text.length() <= 50) return text;
        return text.substring(0, 50) + "...";
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}