package com.openmanus.saa.service.session;

import com.openmanus.saa.model.session.TokenUsage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TokenUsageExtractorTest {

    private TokenUsageExtractor extractor = new TokenUsageExtractor();

    @Test
    void extractReturnsNullForNullResponse() {
        TokenUsage result = extractor.extract(null);
        assertThat(result).isNull();
    }

    @Test
    void extractReturnsNullForNullMetadata() {
        ChatResponse response = mock(ChatResponse.class);
        when(response.getMetadata()).thenReturn(null);

        TokenUsage result = extractor.extract(response);

        assertThat(result).isNull();
    }

    @Test
    void extractReturnsNullForNullUsage() {
        ChatResponse response = mock(ChatResponse.class);
        when(response.getMetadata()).thenReturn(mock());
        when(response.getMetadata().getUsage()).thenReturn(null);

        TokenUsage result = extractor.extract(response);

        assertThat(result).isNull();
    }

    @Test
    void extractReturnsUsageFromResponse() {
        ChatResponse response = mock(ChatResponse.class);
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(Integer.valueOf(100));
        when(response.getMetadata()).thenReturn(mock());
        when(response.getMetadata().getUsage()).thenReturn(usage);

        TokenUsage result = extractor.extract(response);

        assertThat(result).isEqualTo(new TokenUsage(100, 0, 0, 0));
    }

    @Test
    void extractHandlesNullTokenCounts() {
        ChatResponse response = mock(ChatResponse.class);
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(null);
        when(response.getMetadata()).thenReturn(mock());
        when(response.getMetadata().getUsage()).thenReturn(usage);

        TokenUsage result = extractor.extract(response);

        assertThat(result).isEqualTo(new TokenUsage(0, 0, 0, 0));
    }
}
