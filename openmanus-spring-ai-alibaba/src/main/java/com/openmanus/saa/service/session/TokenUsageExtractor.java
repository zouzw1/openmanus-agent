package com.openmanus.saa.service.session;

import com.openmanus.saa.model.session.TokenUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

@Component
public class TokenUsageExtractor {

    public TokenUsage extract(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }

        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return null;
        }

        return new TokenUsage(
            usage.getPromptTokens() != null ? usage.getPromptTokens() : 0,
            0,
            0,
            0
        );
    }
}
