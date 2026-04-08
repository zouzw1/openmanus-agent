package com.openmanus.saa.service.session;

import com.openmanus.saa.config.SessionConfig;
import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.HumanFeedbackResponse;
import com.openmanus.saa.model.context.WorkflowState;
import com.openmanus.saa.model.session.*;
import com.openmanus.saa.service.session.storage.SessionStorage;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SessionMemoryService {

    private static final Logger log = LoggerFactory.getLogger(SessionMemoryService.class);
    private static final String USER_PREFERENCES_KEY = "userPreferences";
    private static final String WORKFLOW_STATE_KEY = "workflowState";

    private final SessionStorage storage;
    private final SessionCompactor compactor;
    private final SessionConfig config;

    public SessionMemoryService(SessionStorage storage, SessionCompactor compactor, SessionConfig config) {
        this.storage = storage;
        this.compactor = compactor;
        this.config = config;
        log.info("SessionMemoryService initialized with storage: {}, compaction: {}, ttl: {}",
            config.getStorage(), config.isCompactionEnabled(), config.getSessionTtl());
    }

    public Session getOrCreate(String sessionId) {
        String resolvedId = resolveSessionId(sessionId);

        Optional<Session> existing = storage.findById(resolvedId);
        if (existing.isPresent()) {
            Session session = existing.get();

            // 自动压缩检查
            if (config.isCompactionEnabled()) {
                if (compactor.shouldCompact(session, config.getCompaction())) {
                    CompactionResult result = compactor.compactSession(session, config.getCompaction());
                    if (result.wasCompacted()) {
                        Session compactedSession = result.compactedSession();
                        storage.save(compactedSession);
                        log.info("Auto-compacted session {}: {} messages removed",
                            resolvedId, result.removedMessageCount());
                        return compactedSession;
                    }
                }
            }

            return session;
        }

        return storage.save(new Session(resolvedId));
    }

    /**
     * 保存会话（用于不可变 Session 操作后的持久化）
     */
    public Session saveSession(Session session) {
        return storage.save(session);
    }

    /**
     * @deprecated 使用 {@link #getOrCreate(String)} 代替
     */
    @Deprecated
    public Session getOrCreateWithCompaction(String sessionId) {
        return getOrCreate(sessionId);
    }

    public SessionStateResponse getState(String sessionId) {
        return storage.findById(sessionId)
            .map(this::toResponse)
            .orElse(null);
    }

    public List<SessionStateResponse> listStates() {
        return storage.findAll().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public boolean delete(String sessionId) {
        storage.deleteById(sessionId);
        return true;
    }

    public String summarizeHistory(Session session, int maxMessages) {
        int start = Math.max(0, session.messages().size() - maxMessages);
        return session.messages().subList(start, session.messages().size()).stream()
            .map(message -> message.role().name().toLowerCase() + ": " +
                message.blocks().stream()
                    .map(b -> b.asText())
                    .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b))
            .collect(Collectors.joining("\n"));
    }

    public void savePendingFeedback(String sessionId, HumanFeedbackRequest request) {
        Session session = storage.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        WorkflowState state = WorkflowState.paused(
            request.getObjective(),
            request.getPlanId(),
            request.getStepIndex(),
            request.getSteps(),
            request
        );

        Session updated = session.putMemory(WORKFLOW_STATE_KEY, state);
        storage.save(updated);

        log.info("Saved pending feedback for session {}: stepIndex={}, objective='{}'",
            sessionId, request.getStepIndex(), request.getObjective());
    }

    public Optional<HumanFeedbackRequest> getPendingFeedback(String sessionId) {
        return getWorkflowState(sessionId)
            .filter(WorkflowState::isPaused)
            .map(WorkflowState::pendingFeedback);
    }

    public void clearPendingFeedback(String sessionId) {
        clearWorkflowState(sessionId);
        log.info("Cleared pending feedback for session {}", sessionId);
    }

    public boolean hasPendingFeedback(String sessionId) {
        return getWorkflowState(sessionId)
            .map(WorkflowState::isPaused)
            .orElse(false);
    }

    // ========== 用户偏好 ==========

    /**
     * 保存用户偏好到 Session workingMemory。
     */
    public Session saveUserPreference(String sessionId, String key, Object value) {
        Session session = storage.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        @SuppressWarnings("unchecked")
        Map<String, Object> prefs = session.getMemory(USER_PREFERENCES_KEY, Map.class)
            .orElse(new HashMap<>());

        Map<String, Object> newPrefs = new HashMap<>(prefs);
        newPrefs.put(key, value);

        Session updated = session.putMemory(USER_PREFERENCES_KEY, Map.copyOf(newPrefs));
        return storage.save(updated);
    }

    /**
     * 获取用户偏好。
     */
    public Optional<Object> getUserPreference(String sessionId, String key) {
        return storage.findById(sessionId)
            .flatMap(session -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> prefs = session.getMemory(USER_PREFERENCES_KEY, Map.class).orElse(null);
                if (prefs == null) return Optional.empty();
                return Optional.ofNullable(prefs.get(key));
            });
    }

    /**
     * 获取所有用户偏好。
     */
    public Map<String, Object> getUserPreferences(String sessionId) {
        return storage.findById(sessionId)
            .flatMap(session -> session.getMemory(USER_PREFERENCES_KEY, Map.class))
            .orElse(Map.of());
    }

    // ========== 工作流状态 ==========

    /**
     * 保存工作流状态到 Session workingMemory。
     */
    public void saveWorkflowState(String sessionId, WorkflowState state) {
        Session session = storage.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        Session updated = session.putMemory(WORKFLOW_STATE_KEY, state);
        storage.save(updated);
        log.debug("Saved workflow state for session {}: {}", sessionId, state.status());
    }

    /**
     * 获取工作流状态。
     */
    public Optional<WorkflowState> getWorkflowState(String sessionId) {
        return storage.findById(sessionId)
            .flatMap(session -> session.getMemory(WORKFLOW_STATE_KEY, WorkflowState.class));
    }

    /**
     * 清除工作流状态。
     */
    public void clearWorkflowState(String sessionId) {
        Session session = storage.findById(sessionId).orElse(null);
        if (session != null) {
            Session updated = session.removeMemory(WORKFLOW_STATE_KEY);
            storage.save(updated);
            log.debug("Cleared workflow state for session {}", sessionId);
        }
    }

    public void processFeedback(String sessionId, HumanFeedbackResponse feedback) {
        log.info("Processing human feedback for session {} with action {}", sessionId, feedback.getAction());
    }

    public boolean tryStartWorkflowExecution(String sessionId, String executionId) {
        return true;
    }

    public void finishWorkflowExecution(String sessionId, String executionId) {
        // no-op
    }

    public boolean hasActiveWorkflowExecution(String sessionId) {
        return false;
    }

    /**
     * 手动压缩指定会话
     */
    public CompactionResult compactSession(String sessionId) {
        Session session = storage.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        CompactionResult result = compactor.compactSession(session, config.getCompaction());
        if (result.wasCompacted()) {
            storage.save(result.compactedSession());
        }
        return result;
    }

    /**
     * 获取压缩统计信息
     */
    public Map<String, Object> getCompactionStats(String sessionId) {
        return storage.findById(sessionId)
            .map(session -> Map.<String, Object>of(
                "sessionId", sessionId,
                "messageCount", session.messages().size(),
                "estimatedTokens", session.estimateTokens(),
                "workingMemoryKeys", session.workingMemory().keySet()
            ))
            .orElse(Map.of());
    }

    /**
     * 定时清理过期会话
     */
    @Scheduled(fixedRateString = "${openmanus.session.cleanup-interval:60000}")
    public void cleanupExpiredSessions() {
        if (config.getSessionTtl().isZero() || config.getSessionTtl().isNegative()) {
            return;
        }

        Instant threshold = Instant.now().minus(config.getSessionTtl());
        List<Session> expired = storage.findExpired(threshold);

        if (!expired.isEmpty()) {
            log.info("Cleaning up {} expired sessions (threshold: {})", expired.size(), threshold);
            for (Session session : expired) {
                storage.deleteById(session.sessionId());
                log.debug("Cleaned up expired session: {}", session.sessionId());
            }
        }
    }

    /**
     * 获取存储统计信息
     */
    public Map<String, Object> getStorageStats() {
        return Map.of(
            "storageType", config.getStorage(),
            "sessionCount", storage.count(),
            "compactionEnabled", config.isCompactionEnabled(),
            "sessionTtl", config.getSessionTtl().toString()
        );
    }

    private String resolveSessionId(String sessionId) {
        return (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
    }

    private SessionStateResponse toResponse(Session session) {
        return new SessionStateResponse(
            session.sessionId(),
            session.createdAt(),
            session.updatedAt(),
            List.copyOf(session.messages()),
            session.getExecutionLogList()
        );
    }
}