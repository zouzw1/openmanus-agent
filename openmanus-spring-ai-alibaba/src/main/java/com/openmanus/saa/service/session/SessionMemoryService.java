package com.openmanus.saa.service.session;

import com.openmanus.saa.model.session.SessionState;
import com.openmanus.saa.model.session.SessionStateResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SessionMemoryService {

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

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
        return sessions.remove(sessionId) != null;
    }

    public String summarizeHistory(SessionState state, int maxMessages) {
        int start = Math.max(0, state.getMessages().size() - maxMessages);
        return state.getMessages().subList(start, state.getMessages().size()).stream()
                .map(message -> message.role() + ": " + message.content())
                .collect(Collectors.joining("\n"));
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
