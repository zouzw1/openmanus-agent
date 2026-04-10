package com.openmanus.saa.service.session;

import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.model.session.ConversationSession;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 会话管理器。
 * 管理ConversationSession的生命周期，支持创建、获取、清理。
 *
 * <p>与现有SessionMemoryService的区别：
 * <ul>
 *   <li>SessionMemoryService：管理SessionState（用户对话历史）</li>
 *   <li>SessionManager：管理ConversationSession（Workflow执行上下文）</li>
 * </ul>
 */
@Component
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final Map<String, ConversationSession> sessions = new ConcurrentHashMap<>();
    private final OpenManusProperties properties;

    // 默认配置
    private static final int DEFAULT_MAX_MESSAGES = 50;
    private static final int DEFAULT_MAX_CHARS = 32000;
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    public SessionManager(OpenManusProperties properties) {
        this.properties = properties;
    }

    /**
     * 获取或创建会话。
     *
     * @param sessionId 会话ID（通常与WorkflowService的sessionId一致）
     * @return 会话实例
     */
    public ConversationSession getOrCreate(String sessionId) {
        return sessions.computeIfAbsent(sessionId, this::createNewSession);
    }

    /**
     * 获取现有会话。
     */
    public Optional<ConversationSession> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * 检查会话是否存在。
     */
    public boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    /**
     * 创建新会话（不存储）。
     */
    public ConversationSession createNewSession(String sessionId) {
        int maxMessages = getMaxMessages();
        int maxChars = getMaxChars();

        ConversationSession session = new ConversationSession(sessionId, maxMessages, maxChars);
        log.info("Created new conversation session: {} (maxMessages={}, maxChars={})",
                sessionId, maxMessages, maxChars);
        return session;
    }

    /**
     * 移除会话。
     */
    public Optional<ConversationSession> remove(String sessionId) {
        ConversationSession removed = sessions.remove(sessionId);
        if (removed != null) {
            log.info("Removed conversation session: {}", sessionId);
        }
        return Optional.ofNullable(removed);
    }

    /**
     * 清理会话（清空内容但不移除）。
     */
    public void clearSession(String sessionId) {
        get(sessionId).ifPresent(ConversationSession::clear);
    }

    /**
     * 清理过期会话。
     *
     * @return 清理的会话数量
     */
    public int cleanupExpiredSessions() {
        Duration ttl = getTTL();
        Instant threshold = Instant.now().minus(ttl);

        List<String> expiredIds = sessions.entrySet().stream()
                .filter(entry -> entry.getValue().getLastAccessedAt().isBefore(threshold))
                .map(Map.Entry::getKey)
                .toList();

        expiredIds.forEach(sessions::remove);

        if (!expiredIds.isEmpty()) {
            log.info("Cleaned up {} expired conversation sessions", expiredIds.size());
        }

        return expiredIds.size();
    }

    /**
     * 获取活跃会话数量。
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * 获取所有活跃会话ID。
     */
    public List<String> getActiveSessionIds() {
        return List.copyOf(sessions.keySet());
    }

    /**
     * 清空所有会话。
     */
    public void clearAll() {
        int count = sessions.size();
        sessions.clear();
        log.info("Cleared all {} conversation sessions", count);
    }

    // ================== 配置获取方法 ==================

    private int getMaxMessages() {
        if (properties == null || properties.getMultiAgent() == null) {
            return DEFAULT_MAX_MESSAGES;
        }
        return properties.getMultiAgent().getContextMaxTurns() > 0
                ? properties.getMultiAgent().getContextMaxTurns()
                : DEFAULT_MAX_MESSAGES;
    }

    private int getMaxChars() {
        if (properties == null || properties.getMultiAgent() == null) {
            return DEFAULT_MAX_CHARS;
        }
        return properties.getMultiAgent().getContextMaxChars() > 0
                ? properties.getMultiAgent().getContextMaxChars()
                : DEFAULT_MAX_CHARS;
    }

    private Duration getTTL() {
        return DEFAULT_TTL;
    }
}
