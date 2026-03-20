package com.openmanus.saa.model.session;

import java.time.Instant;
import java.util.List;

public record SessionStateResponse(
        String sessionId,
        Instant createdAt,
        Instant updatedAt,
        List<ConversationMessage> messages,
        List<String> executionLog
) {
}
