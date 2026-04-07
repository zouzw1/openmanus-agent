package com.openmanus.saa.service.session;

import com.openmanus.saa.config.SessionConfig;
import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.HumanFeedbackResponse;
import com.openmanus.saa.model.session.CompactionResult;
import com.openmanus.saa.model.session.SessionState;
import com.openmanus.saa.model.session.SessionStateResponse;
import com.openmanus.saa.service.session.storage.SessionStorage;
import java.time.Instant;
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

    public SessionState getOrCreate(String sessionId) {
        String resolvedId = resolveSessionId(sessionId);

        Optional<SessionState> existing = storage.findById(resolvedId);
        if (existing.isPresent()) {
            SessionState session = existing.get();

            // 更新最后访问时间
            session.touch();

            // 自动压缩检查
            if (config.isCompactionEnabled()) {
                if (compactor.shouldCompact(session, config.getCompaction())) {
                    CompactionResult result = compactor.compact(session, config.getCompaction());
                    if (result.wasCompacted()) {
                        storage.save(result.compactedSession());
                        log.info("Auto-compacted session {}: {} messages removed",
                            resolvedId, result.removedMessageCount());
                        return result.compactedSession();
                    }
                }
            }

            return session;
        }

        return storage.save(new SessionState(resolvedId));
    }

    /**
     * @deprecated 使用 {@link #getOrCreate(String)} 代替
     */
    @Deprecated
    public SessionState getOrCreateWithCompaction(String sessionId) {
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

    public String summarizeHistory(SessionState state, int maxMessages) {
        int start = Math.max(0, state.getMessages().size() - maxMessages);
        return state.getMessages().subList(start, state.getMessages().size()).stream()
            .map(message -> message.role().name().toLowerCase() + ": " +
                message.blocks().stream()
                    .map(b -> b.asText())
                    .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b))
            .collect(Collectors.joining("\n"));
    }

    public void savePendingFeedback(String sessionId, HumanFeedbackRequest request) {
        log.info("Saved pending feedback for session {}: stepIndex={}, objective='{}'",
            sessionId, request.getStepIndex(), request.getObjective());
    }

    public Optional<HumanFeedbackRequest> getPendingFeedback(String sessionId) {
        return Optional.empty();
    }

    public void clearPendingFeedback(String sessionId) {
        log.info("Cleared pending feedback for session {}", sessionId);
    }

    public boolean hasPendingFeedback(String sessionId) {
        return false;
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
        SessionState session = storage.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        CompactionResult result = compactor.compact(session, config.getCompaction());
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
                "messageCount", session.getMessages().size(),
                "estimatedTokens", session.estimateTokens(),
                "hasCompactedSummary", session.getCompactedSummary().isPresent()
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
        List<SessionState> expired = storage.findExpired(threshold);

        if (!expired.isEmpty()) {
            log.info("Cleaning up {} expired sessions (threshold: {})", expired.size(), threshold);
            for (SessionState session : expired) {
                storage.deleteById(session.getSessionId());
                log.debug("Cleaned up expired session: {}", session.getSessionId());
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

    private SessionStateResponse toResponse(SessionState state) {
        return new SessionStateResponse(
            state.getSessionId(),
            state.getCreatedAt(),
            state.getUpdatedAt(),
            List.copyOf(state.getMessages()),
            List.copyOf(state.getExecutionLog())
        );
    }
}
