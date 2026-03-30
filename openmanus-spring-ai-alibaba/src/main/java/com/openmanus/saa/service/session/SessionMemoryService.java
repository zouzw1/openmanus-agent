package com.openmanus.saa.service.session;

import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.HumanFeedbackResponse;
import com.openmanus.saa.model.session.SessionState;
import com.openmanus.saa.model.session.SessionStateResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SessionMemoryService {

    private static final Logger log = LoggerFactory.getLogger(SessionMemoryService.class);

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final Map<String, HumanFeedbackRequest> pendingFeedbacks = new ConcurrentHashMap<>();
    private final Map<String, String> activeWorkflowExecutions = new ConcurrentHashMap<>();

    public SessionState getOrCreate(String sessionId) {
        String resolvedId = (sessionId == null || sessionId.isBlank())
                ? UUID.randomUUID().toString()
                : sessionId;
        return sessions.computeIfAbsent(resolvedId, SessionState::new);
    }

    public SessionStateResponse getState(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return null;
        }
        return toResponse(state);
    }

    public List<SessionStateResponse> listStates() {
        return sessions.values().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public boolean delete(String sessionId) {
        pendingFeedbacks.remove(sessionId);
        return sessions.remove(sessionId) != null;
    }

    public String summarizeHistory(SessionState state, int maxMessages) {
        int start = Math.max(0, state.getMessages().size() - maxMessages);
        return state.getMessages().subList(start, state.getMessages().size()).stream()
                .map(message -> message.role() + ": " + message.content())
                .collect(Collectors.joining("\n"));
    }

    public void savePendingFeedback(String sessionId, HumanFeedbackRequest request) {
        pendingFeedbacks.put(sessionId, request);
        log.info(
                "Saved pending feedback for session {}: stepIndex={}, objective='{}'",
                sessionId,
                request.getStepIndex(),
                request.getObjective()
        );
    }

    public Optional<HumanFeedbackRequest> getPendingFeedback(String sessionId) {
        HumanFeedbackRequest request = pendingFeedbacks.get(sessionId);
        log.info(
                "Queried pending feedback for session {}: present={}",
                sessionId,
                request != null
        );
        return Optional.ofNullable(request);
    }

    public void clearPendingFeedback(String sessionId) {
        HumanFeedbackRequest removed = pendingFeedbacks.remove(sessionId);
        log.info(
                "Cleared pending feedback for session {}: hadValue={}",
                sessionId,
                removed != null
        );
    }

    public boolean hasPendingFeedback(String sessionId) {
        return pendingFeedbacks.containsKey(sessionId);
    }

    public void processFeedback(String sessionId, HumanFeedbackResponse feedback) {
        SessionState session = sessions.get(sessionId);
        log.info(
                "Processing human feedback for session {} with action {}",
                sessionId,
                feedback.getAction()
        );
        if (session != null) {
            session.addExecutionLog("Human feedback received: " + feedback.getAction());
            if (feedback.getProvidedInfo() != null && !feedback.getProvidedInfo().isBlank()) {
                session.addMessage("user", "[FEEDBACK] " + feedback.getProvidedInfo());
            }
            if (feedback.getModifiedParams() != null && !feedback.getModifiedParams().isBlank()) {
                session.addMessage("user", "[FEEDBACK_PARAMS] " + feedback.getModifiedParams());
            }
            if (feedback.getInferencePolicy() != null) {
                session.setLatestInferencePolicy(feedback.getInferencePolicy());
                session.addExecutionLog("Inference policy updated: " + feedback.getInferencePolicy());
            }
        }
        clearPendingFeedback(sessionId);
    }

    public boolean tryStartWorkflowExecution(String sessionId, String executionId) {
        return activeWorkflowExecutions.putIfAbsent(sessionId, executionId) == null;
    }

    public void finishWorkflowExecution(String sessionId, String executionId) {
        activeWorkflowExecutions.remove(sessionId, executionId);
    }

    public boolean hasActiveWorkflowExecution(String sessionId) {
        return activeWorkflowExecutions.containsKey(sessionId);
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
