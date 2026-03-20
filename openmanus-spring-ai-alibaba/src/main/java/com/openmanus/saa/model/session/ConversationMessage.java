package com.openmanus.saa.model.session;

import java.time.Instant;

public record ConversationMessage(
        String role,
        String content,
        Instant timestamp
) {
}
