package com.openmanus.saa.model.session;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TokenUsageTest {

    @Test
    void zeroReturnsAllZeros() {
        TokenUsage usage = TokenUsage.zero();

        assertThat(usage.inputTokens()).isEqualTo(0);
        assertThat(usage.outputTokens()).isEqualTo(0);
        assertThat(usage.cacheCreationInputTokens()).isEqualTo(0);
        assertThat(usage.cacheReadInputTokens()).isEqualTo(0);
        assertThat(usage.totalTokens()).isEqualTo(0);
    }

    @Test
    void totalTokensSumsAllFields() {
        TokenUsage usage = new TokenUsage(100, 50, 10, 5);

        assertThat(usage.totalTokens()).isEqualTo(165);
    }

    @Test
    void addCombinesTwoUsages() {
        TokenUsage a = new TokenUsage(100, 50, 10, 5);
        TokenUsage b = new TokenUsage(200, 75, 20, 10);

        TokenUsage result = a.add(b);

        assertThat(result).isEqualTo(new TokenUsage(300, 125, 30, 15));
    }

    @Test
    void addWithNullReturnsOriginal() {
        TokenUsage a = new TokenUsage(100, 50, 10, 5);

        TokenUsage result = a.add(null);

        assertThat(result).isEqualTo(a);
    }
}