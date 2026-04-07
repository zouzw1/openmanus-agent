package com.openmanus.saa.model.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenUsage(
    int inputTokens,
    int outputTokens,
    int cacheCreationInputTokens,
    int cacheReadInputTokens
) {
    @JsonCreator
    public TokenUsage(
        @JsonProperty("inputTokens") int inputTokens,
        @JsonProperty("outputTokens") int outputTokens,
        @JsonProperty("cacheCreationInputTokens") int cacheCreationInputTokens,
        @JsonProperty("cacheReadInputTokens") int cacheReadInputTokens
    ) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheCreationInputTokens = cacheCreationInputTokens;
        this.cacheReadInputTokens = cacheReadInputTokens;
    }

    public static TokenUsage zero() {
        return new TokenUsage(0, 0, 0, 0);
    }

    @JsonIgnore
    public int totalTokens() {
        return inputTokens + outputTokens + cacheCreationInputTokens + cacheReadInputTokens;
    }

    @JsonIgnore
    public TokenUsage add(TokenUsage other) {
        if (other == null) return this;
        return new TokenUsage(
            inputTokens + other.inputTokens,
            outputTokens + other.outputTokens,
            cacheCreationInputTokens + other.cacheCreationInputTokens,
            cacheReadInputTokens + other.cacheReadInputTokens
        );
    }
}
