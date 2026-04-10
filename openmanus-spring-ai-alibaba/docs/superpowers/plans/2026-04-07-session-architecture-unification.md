# Session Architecture Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify the dual session system (SessionState + ConversationSession) into a single Session model with ContentBlock abstraction, TokenUsage tracking, and intelligent compaction.

**Architecture:** Merge SessionState and ConversationSession into a unified Session class. Introduce sealed interface ContentBlock (TextBlock/ToolUseBlock/ToolResultBlock) for structured message content. Add TokenUsage for cost tracking. Implement full Claw-style compaction with continuation message and context preservation.

**Tech Stack:** Java 17+, Spring Boot 3, Spring AI, Jackson, JUnit 5, Mockito, AssertJ

---

## File Structure

### New Files

| File | Responsibility |
|------|----------------|
| `model/session/Session.java` | Unified session model (replaces SessionState + ConversationSession) |
| `model/session/MessageRole.java` | Enum: SYSTEM, USER, ASSISTANT, TOOL |
| `model/session/TokenUsage.java` | Record for token usage tracking |
| `model/session/TextBlock.java` | Record implementing ContentBlock |
| `model/session/ToolUseBlock.java` | Record implementing ContentBlock |
| `model/session/ToolResultBlock.java` | Record implementing ContentBlock |
| `model/session/BlockType.java` | Enum for content block types |
| `model/session/CompactionConfig.java` | Record for compaction configuration |
| `model/session/CompactionResult.java` | Record for compaction result |
| `service/session/SessionCompactor.java` | Compaction logic (Claw-style) |
| `service/session/SessionToSpringAIConverter.java` | Convert Session to Spring AI messages |
| `service/session/TokenUsageExtractor.java` | Extract TokenUsage from ChatResponse |
| `service/session/SessionSerializer.java` | Jackson serialization for Session |
| `test/model/session/SessionTest.java` | Unit tests for Session |
| `test/model/session/ContentBlockTest.java` | Unit tests for ContentBlock |
| `test/model/session/TokenUsageTest.java` | Unit tests for TokenUsage |
| `test/service/session/SessionCompactorTest.java` | Unit tests for SessionCompactor |
| `test/service/session/SessionManagerTest.java` | Unit tests for SessionManager |

### Modified Files

| File | Changes |
|------|---------|
| `model/session/ConversationMessage.java` | Update to use List<ContentBlock> + TokenUsage |
| `model/session/SessionMessages.java` | Update to use new ContentBlock types |
| `service/session/SessionManager.java` | Update to use unified Session |
| `service/session/SessionCompactor.java` | Rewrite with Claw-style logic |
| `service/session/storage/SessionStorage.java` | Update interface for Session |
| `service/session/storage/MemorySessionStorage.java` | Update implementation |
| `service/session/storage/MybatisSessionStorage.java` | Update implementation |
| `config/SessionConfig.java` | Add compaction config fields |

### Deleted Files (after migration)

| File | Reason |
|------|--------|
| `model/session/SessionState.java` | Merged into Session |
| `model/session/ConversationSession.java` | Merged into Session |

---

## Task 1: TokenUsage Record

**Files:**
- Create: `src/main/java/com/openmanus/saa/model/session/TokenUsage.java`
- Create: `src/test/java/com/openmanus/saa/model/session/TokenUsageTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/openmanus/saa/model/session/TokenUsageTest.java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=TokenUsageTest -pl openmanus-spring-ai-alibaba`
Expected: FAIL with "TokenUsage cannot be resolved"

- [ ] **Step 3: Write minimal implementation**

```java
// src/main/java/com/openmanus/saa/model/session/TokenUsage.java
package com.openmanus.saa.model.session;

import com.fasterxml.jackson.annotation.JsonCreator;
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

    public int totalTokens() {
        return inputTokens + outputTokens + cacheCreationInputTokens + cacheReadInputTokens;
    }

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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=TokenUsageTest -pl openmanus-spring-ai-alibaba`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/session/TokenUsage.java openmanus-spring-ai-alibaba/src/test/java/com/openmanus/saa/model/session/TokenUsageTest.java
git commit -m "feat: add TokenUsage record for token tracking"
```

---

## Task 2: ContentBlock Types

**Files:**
- Create: `src/main/java/com/openmanus/saa/model/session/BlockType.java`
- Create: `src/main/java/com/openmanus/saa/model/session/TextBlock.java`
- Create: `src/main/java/com/openmanus/saa/model/session/ToolUseBlock.java`
- Create: `src/main/java/com/openmanus/saa/model/session/ToolResultBlock.java`
- Create: `src/test/java/com/openmanus/saa/model/session/ContentBlockTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/openmanus/saa/model/session/ContentBlockTest.java
package com.openmanus.saa.model.session;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ContentBlockTest {

    @Test
    void textBlockReturnsCorrectType() {
        TextBlock block = new TextBlock("Hello");
        
        assertThat(block.getType()).isEqualTo(BlockType.TEXT);
        assertThat(block.text()).isEqualTo("Hello");
    }

    @Test
    void textBlockToSummaryTruncatesLongText() {
        String longText = "x".repeat(200);
        TextBlock block = new TextBlock(longText);
        
        String summary = block.toSummary();
        
        assertThat(summary).hasSizeLessThanOrEqualTo(161);
        assertThat(summary).endsWith("…");
    }

    @Test
    void toolUseBlockReturnsCorrectType() {
        ToolUseBlock block = new ToolUseBlock("id-1", "bash", "echo hello");
        
        assertThat(block.getType()).isEqualTo(BlockType.TOOL_USE);
        assertThat(block.id()).isEqualTo("id-1");
        assertThat(block.name()).isEqualTo("bash");
        assertThat(block.input()).isEqualTo("echo hello");
    }

    @Test
    void toolUseBlockToSummaryContainsToolName() {
        ToolUseBlock block = new ToolUseBlock("id-1", "bash", "echo hello");
        
        String summary = block.toSummary();
        
        assertThat(summary).contains("tool_use");
        assertThat(summary).contains("bash");
    }

    @Test
    void toolResultBlockReturnsCorrectType() {
        ToolResultBlock block = new ToolResultBlock("id-1", "bash", "hello", false);
        
        assertThat(block.getType()).isEqualTo(BlockType.TOOL_RESULT);
        assertThat(block.toolUseId()).isEqualTo("id-1");
        assertThat(block.isError()).isFalse();
    }

    @Test
    void toolResultBlockToSummaryContainsErrorWhenIsError() {
        ToolResultBlock block = new ToolResultBlock("id-1", "bash", "failed", true);
        
        String summary = block.toSummary();
        
        assertThat(summary).contains("error");
        assertThat(summary).contains("tool_result");
    }

    @Test
    void toolResultBlockToSummaryOmitsErrorWhenNotError() {
        ToolResultBlock block = new ToolResultBlock("id-1", "bash", "success", false);
        
        String summary = block.toSummary();
        
        assertThat(summary).doesNotContain("error");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ContentBlockTest -pl openmanus-spring-ai-alibaba`
Expected: FAIL with "BlockType/TextBlock/ToolUseBlock/ToolResultBlock cannot be resolved"

- [ ] **Step 3: Write BlockType enum**

```java
// src/main/java/com/openmanus/saa/model/session/BlockType.java
package com.openmanus.saa.model.session;

public enum BlockType {
    TEXT,
    TOOL_USE,
    TOOL_RESULT
}
```

- [ ] **Step 4: Write ContentBlock interface and implementations**

```java
// src/main/java/com/openmanus/saa/model/session/ContentBlock.java
package com.openmanus.saa.model.session;

public sealed interface ContentBlock permits TextBlock, ToolUseBlock, ToolResultBlock {
    String toSummary();
    BlockType getType();
}
```

```java
// src/main/java/com/openmanus/saa/model/session/TextBlock.java
package com.openmanus.saa.model.session;

public record TextBlock(String text) implements ContentBlock {
    @Override
    public BlockType getType() {
        return BlockType.TEXT;
    }

    @Override
    public String toSummary() {
        return truncate(text, 160);
    }

    private static String truncate(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "…";
    }
}
```

```java
// src/main/java/com/openmanus/saa/model/session/ToolUseBlock.java
package com.openmanus.saa.model.session;

public record ToolUseBlock(String id, String name, String input) implements ContentBlock {
    @Override
    public BlockType getType() {
        return BlockType.TOOL_USE;
    }

    @Override
    public String toSummary() {
        return "tool_use " + name + "(" + truncate(input, 100) + ")";
    }

    private static String truncate(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "…";
    }
}
```

```java
// src/main/java/com/openmanus/saa/model/session/ToolResultBlock.java
package com.openmanus.saa.model.session;

public record ToolResultBlock(
    String toolUseId,
    String toolName,
    String output,
    boolean isError
) implements ContentBlock {
    @Override
    public BlockType getType() {
        return BlockType.TOOL_RESULT;
    }

    @Override
    public String toSummary() {
        String prefix = isError ? "error " : "";
        return prefix + "tool_result " + toolName + ": " + truncate(output, 100);
    }

    private static String truncate(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "…";
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ContentBlockTest -pl openmanus-spring-ai-alibaba`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/session/BlockType.java openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/session/ContentBlock.java openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/session/TextBlock.java openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/session/ToolUseBlock.java openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/session/ToolResultBlock.java openmanus-spring-ai-alibaba/src/test/java/com/openmanus/saa/model/session/ContentBlockTest.java
git commit -m "feat: add ContentBlock types (TextBlock, ToolUseBlock, ToolResultBlock)"
```

---

## Task 3: MessageRole Enum

**Files:**
- Create: `src/main/java/com/openmanus/saa/model/session/MessageRole.java`

- [ ] **Step 1: Write MessageRole enum**

```java
// src/main/java/com/openmanus/saa/model/session/MessageRole.java
package com.openmanus.saa.model.session;

public enum MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}
```

- [ ] **Step 2: Commit**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/session/MessageRole.java
git commit -m "feat: add MessageRole enum"
```

---

## Task 4: ConversationMessage Update

**Files:**
- Modify: `src/main/java/com/openmanus/saa/model/session/ConversationMessage.java`
- Create: `src/test/java/com/openmanus/saa/model/session/ConversationMessageTest.java`

- [ ] **Step 1: Read existing ConversationMessage**

Read: `src/main/java/com/openmanus/saa/model/session/ConversationMessage.java`
Note current structure and identify usages.

- [ ] **Step 2: Write the failing test**

```java
// src/test/java/com/openmanus/saa/model/session/ConversationMessageTest.java
package com.openmanus.saa.model.session;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ConversationMessageTest {

    @Test
    void userTextCreatesMessageWithTextBlock() {
        ConversationMessage msg = ConversationMessage.userText("Hello");
        
        assertThat(msg.role()).isEqualTo(MessageRole.USER);
        assertThat(msg.blocks()).hasSize(1);
        assertThat(msg.blocks().get(0)).isInstanceOf(TextBlock.class);
        assertThat(((TextBlock) msg.blocks().get(0)).text()).isEqualTo("Hello");
        assertThat(msg.usage()).isNull();
    }

    @Test
    void assistantCreatesMessageWithBlocks() {
        List<ContentBlock> blocks = List.of(
            new TextBlock("thinking"),
            new ToolUseBlock("id-1", "bash", "echo hi")
        );
        
        ConversationMessage msg = ConversationMessage.assistant(blocks);
        
        assertThat(msg.role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(msg.blocks()).hasSize(2);
        assertThat(msg.usage()).isNull();
    }

    @Test
    void assistantWithUsageIncludesTokenUsage() {
        List<ContentBlock> blocks = List.of(new TextBlock("response"));
        TokenUsage usage = new TokenUsage(100, 50, 10, 5);
        
        ConversationMessage msg = ConversationMessage.assistantWithUsage(blocks, usage);
        
        assertThat(msg.usage()).isEqualTo(usage);
    }

    @Test
    void toolResultCreatesToolMessage() {
        ConversationMessage msg = ConversationMessage.toolResult("id-1", "bash", "hello", false);
        
        assertThat(msg.role()).isEqualTo(MessageRole.TOOL);
        assertThat(msg.blocks()).hasSize(1);
        assertThat(msg.blocks().get(0)).isInstanceOf(ToolResultBlock.class);
    }

    @Test
    void systemCreatesSystemMessage() {
        ConversationMessage msg = ConversationMessage.system("System prompt");
        
        assertThat(msg.role()).isEqualTo(MessageRole.SYSTEM);
        assertThat(((TextBlock) msg.blocks().get(0)).text()).isEqualTo("System prompt");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ConversationMessageTest -pl openmanus-spring-ai-alibaba`
Expected: FAIL (existing ConversationMessage doesn't have blocks/usage fields)

- [ ] **Step 3: Update ConversationMessage**

```java
// src/main/java/com/openmanus/saa/model/session/ConversationMessage.java
package com.openmanus.saa.model.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record ConversationMessage(
    MessageRole role,
    List<ContentBlock> blocks,
    TokenUsage usage,
    Instant timestamp
) {
    @JsonCreator
    public ConversationMessage(
        @JsonProperty("role") MessageRole role,
        @JsonProperty("blocks") List<ContentBlock> blocks,
        @JsonProperty("usage") TokenUsage usage,
        @JsonProperty("timestamp") Instant timestamp
    ) {
        this.role = role;
        this.blocks = blocks != null ? List.copyOf(blocks) : List.of();
        this.usage = usage;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public static ConversationMessage userText(String text) {
        return new ConversationMessage(
            MessageRole.USER,
            List.of(new TextBlock(text)),
            null,
            Instant.now()
        );
    }

    public static ConversationMessage assistant(List<ContentBlock> blocks) {
        return new ConversationMessage(MessageRole.ASSISTANT, blocks, null, Instant.now());
    }

    public static ConversationMessage assistantWithUsage(List<ContentBlock> blocks, TokenUsage usage) {
        return new ConversationMessage(MessageRole.ASSISTANT, blocks, usage, Instant.now());
    }

    public static ConversationMessage toolResult(String toolUseId, String toolName, String output, boolean isError) {
        return new ConversationMessage(
            MessageRole.TOOL,
            List.of(new ToolResultBlock(toolUseId, toolName, output, isError)),
            null,
            Instant.now()
        );
    }

    public static ConversationMessage system(String content) {
        return new ConversationMessage(
            MessageRole.SYSTEM,
            List.of(new TextBlock(content)),
            null,
            Instant.now()
        );
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ConversationMessageTest -pl openmanus-spring-ai-alibaba`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/session/ConversationMessage.java openmanus-spring-ai-alibaba/src/test/java/com/openmanus/saa/model/session/ConversationMessageTest.java
git commit -m "feat: update ConversationMessage with ContentBlock and TokenUsage"
```

---

## Task 5: CompactionConfig and CompactionResult

**Files:**
- Create: `src/main/java/com/openmanus/saa/model/session/CompactionConfig.java` (rewrite as record)
- Create: `src/main/java/com/openmanus/saa/model/session/CompactionResult.java` (rewrite as record)

- [ ] **Step 1: Read existing files**

Read: `src/main/java/com/openmanus/saa/model/session/CompactionConfig.java`
Read: `src/main/java/com/openmanus/saa/model/session/CompactionResult.java`

- [ ] **Step 2: Rewrite CompactionConfig as record**

```java
// src/main/java/com/openmanus/saa/model/session/CompactionConfig.java
package com.openmanus.saa.model.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CompactionConfig(
    int preserveRecentMessages,
    int maxEstimatedTokens
) {
    public static final CompactionConfig DEFAULT = new CompactionConfig(4, 10000);

    @JsonCreator
    public CompactionConfig(
        @JsonProperty("preserveRecentMessages") int preserveRecentMessages,
        @JsonProperty("maxEstimatedTokens") int maxEstimatedTokens
    ) {
        this.preserveRecentMessages = preserveRecentMessages;
        this.maxEstimatedTokens = maxEstimatedTokens;
    }

    public static CompactionConfig defaultConfig() {
        return DEFAULT;
    }
}
```

- [ ] **Step 3: Rewrite CompactionResult as record**

```java
// src/main/java/com/openmanus/saa/model/session/CompactionResult.java
package com.openmanus.saa.model.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CompactionResult(
    String summary,
    String formattedSummary,
    Session compactedSession,
    int removedMessageCount
) {
    @JsonCreator
    public CompactionResult(
        @JsonProperty("summary") String summary,
        @JsonProperty("formattedSummary") String formattedSummary,
        @JsonProperty("compactedSession") Session compactedSession,
        @JsonProperty("removedMessageCount") int removedMessageCount
    ) {
        this.summary = summary;
        this.formattedSummary = formattedSummary;
        this.compactedSession = compactedSession;
        this.removedMessageCount = removedMessageCount;
    }

    public static CompactionResult unchanged(Session session) {
        return new CompactionResult("", "", session, 0);
    }

    public boolean wasCompacted() {
        return removedMessageCount > 0;
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/session/CompactionConfig.java openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/session/CompactionResult.java
git commit -m "refactor: rewrite CompactionConfig and CompactionResult as records"
```

---

## Task 6: Unified Session Model

**Files:**
- Create: `src/main/java/com/openmanus/saa/model/session/Session.java`
- Create: `src/test/java/com/openmanus/saa/model/session/SessionTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/openmanus/saa/model/session/SessionTest.java
package com.openmanus.saa.model.session;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class SessionTest {

    @Test
    void createSessionWithDefaults() {
        Session session = new Session("test-session-1");
        
        assertThat(session.sessionId()).isEqualTo("test-session-1");
        assertThat(session.version()).isEqualTo(1);
        assertThat(session.messages()).isEmpty();
        assertThat(session.workingMemory()).isEmpty();
        assertThat(session.executionLog()).isEmpty();
        assertThat(session.cumulativeUsage()).isEqualTo(TokenUsage.zero());
    }

    @Test
    void addMessageIncreasesMessageCount() {
        Session session = new Session("test-session-1");
        
        session = session.addMessage(ConversationMessage.userText("Hello"));
        
        assertThat(session.messages()).hasSize(1);
        assertThat(session.messages().get(0).role()).isEqualTo(MessageRole.USER);
    }

    @Test
    void addSystemMessageAddsSystemMessage() {
        Session session = new Session("test-session-1");
        
        session = session.addSystemMessage("System prompt");
        
        assertThat(session.messages()).hasSize(1);
        assertThat(session.messages().get(0).role()).isEqualTo(MessageRole.SYSTEM);
    }

    @Test
    void recordToolCallAddsToolUseMessage() {
        Session session = new Session("test-session-1");
        
        session = session.recordToolCall("bash", "echo hello");
        
        assertThat(session.messages()).hasSize(1);
        assertThat(session.messages().get(0).role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(session.messages().get(0).blocks()).hasSize(1);
        assertThat(session.messages().get(0).blocks().get(0)).isInstanceOf(ToolUseBlock.class);
    }

    @Test
    void recordToolResultAddsToolResultMessage() {
        Session session = new Session("test-session-1");
        
        session = session.recordToolResult("tool-1", "bash", "hello", false);
        
        assertThat(session.messages()).hasSize(1);
        assertThat(session.messages().get(0).role()).isEqualTo(MessageRole.TOOL);
        assertThat(session.messages().get(0).blocks().get(0)).isInstanceOf(ToolResultBlock.class);
    }

    @Test
    void putAndGetMemory() {
        Session session = new Session("test-session-1");
        
        session = session.putMemory("key1", "value1");
        
        assertThat(session.getMemory("key1")).contains("value1");
        assertThat(session.getMemory("missing")).isEmpty();
    }

    @Test
    void typedMemoryReturnsCorrectType() {
        Session session = new Session("test-session-1");
        
        session = session.putMemory("key1", 42);
        
        assertThat(session.getMemory("key1", Integer.class)).contains(42);
        assertThat(session.getMemory("key1", String.class)).isEmpty();
    }

    @Test
    void removeMemoryRemovesEntry() {
        Session session = new Session("test-session-1");
        session = session.putMemory("key1", "value1");
        
        session = session.removeMemory("key1");
        
        assertThat(session.getMemory("key1")).isEmpty();
    }

    @Test
    void addExecutionLogAddsEntry() {
        Session session = new Session("test-session-1");
        
        session = session.addExecutionLog("Step 1 executed");
        
        assertThat(session.executionLog()).containsExactly("Step 1 executed");
    }

    @Test
    void isEmptyReturnsTrueForNewSession() {
        Session session = new Session("test-session-1");
        
        assertThat(session.isEmpty()).isTrue();
    }

    @Test
    void isEmptyReturnsFalseWhenHasMessages() {
        Session session = new Session("test-session-1");
        session = session.addMessage(ConversationMessage.userText("Hello"));
        
        assertThat(session.isEmpty()).isFalse();
    }

    @Test
    void isEmptyReturnsFalseWhenHasMemory() {
        Session session = new Session("test-session-1");
        session = session.putMemory("key", "value");
        
        assertThat(session.isEmpty()).isFalse();
    }

    @Test
    void clearRemovesAllData() {
        Session session = new Session("test-session-1");
        session = session.addMessage(ConversationMessage.userText("Hello"));
        session = session.putMemory("key", "value");
        session = session.addExecutionLog("log");
        
        session = session.clear();
        
        assertThat(session.isEmpty()).isTrue();
        assertThat(session.messages()).isEmpty();
        assertThat(session.workingMemory()).isEmpty();
        assertThat(session.executionLog()).isEmpty();
    }

    @Test
    void estimateTokensCalculatesApproximately() {
        Session session = new Session("test-session-1");
        session = session.addMessage(ConversationMessage.userText("Hello World")); // 11 chars ≈ 3 tokens
        
        int tokens = session.estimateTokens();
        
        assertThat(tokens).isGreaterThanOrEqualTo(3);
    }

    @Test
    void updateCumulativeUsageAddsToTotal() {
        Session session = new Session("test-session-1");
        TokenUsage usage1 = new TokenUsage(100, 50, 10, 5);
        TokenUsage usage2 = new TokenUsage(200, 75, 20, 10);
        
        session = session.updateCumulativeUsage(usage1);
        session = session.updateCumulativeUsage(usage2);
        
        assertThat(session.cumulativeUsage()).isEqualTo(new TokenUsage(300, 125, 30, 15));
    }

    @Test
    void withMessagesCreatesNewSessionWithMessages() {
        Session session = new Session("test-session-1");
        session = session.putMemory("key", "value");
        
        List<ConversationMessage> newMessages = List.of(
            ConversationMessage.userText("New message")
        );
        Session newSession = session.withMessages(newMessages);
        
        assertThat(newSession.messages()).hasSize(1);
        assertThat(newSession.workingMemory()).containsEntry("key", "value");
        assertThat(newSession.sessionId()).isEqualTo("test-session-1");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SessionTest -pl openmanus-spring-ai-alibaba`
Expected: FAIL with "Session cannot be resolved"

- [ ] **Step 3: Write Session implementation**

```java
// src/main/java/com/openmanus/saa/model/session/Session.java
package com.openmanus.saa.model.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.*;

public record Session(
    int version,
    String sessionId,
    Instant createdAt,
    Instant updatedAt,
    Instant lastAccessedAt,
    List<ConversationMessage> messages,
    Map<String, Object> workingMemory,
    List<String> executionLog,
    TokenUsage cumulativeUsage,
    InferencePolicy latestInferencePolicy,
    ResponseMode latestResponseMode
) {
    @JsonCreator
    public Session(
        @JsonProperty("version") int version,
        @JsonProperty("sessionId") String sessionId,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt,
        @JsonProperty("lastAccessedAt") Instant lastAccessedAt,
        @JsonProperty("messages") List<ConversationMessage> messages,
        @JsonProperty("workingMemory") Map<String, Object> workingMemory,
        @JsonProperty("executionLog") List<String> executionLog,
        @JsonProperty("cumulativeUsage") TokenUsage cumulativeUsage,
        @JsonProperty("latestInferencePolicy") InferencePolicy latestInferencePolicy,
        @JsonProperty("latestResponseMode") ResponseMode latestResponseMode
    ) {
        this.version = version > 0 ? version : 1;
        this.sessionId = Objects.requireNonNull(sessionId);
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.lastAccessedAt = lastAccessedAt != null ? lastAccessedAt : this.createdAt;
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.workingMemory = workingMemory != null ? new HashMap<>(workingMemory) : new HashMap<>();
        this.executionLog = executionLog != null ? new ArrayList<>(executionLog) : new ArrayList<>();
        this.cumulativeUsage = cumulativeUsage != null ? cumulativeUsage : TokenUsage.zero();
        this.latestInferencePolicy = latestInferencePolicy;
        this.latestResponseMode = latestResponseMode;
    }

    public Session(String sessionId) {
        this(1, sessionId, Instant.now(), Instant.now(), Instant.now(),
             new ArrayList<>(), new HashMap<>(), new ArrayList<>(), TokenUsage.zero(),
             null, null);
    }

    public Session addMessage(ConversationMessage message) {
        List<ConversationMessage> newMessages = new ArrayList<>(this.messages);
        newMessages.add(message);
        return new Session(version, sessionId, createdAt, Instant.now(), Instant.now(),
                          newMessages, workingMemory, executionLog, cumulativeUsage,
                          latestInferencePolicy, latestResponseMode);
    }

    public Session addSystemMessage(String content) {
        return addMessage(ConversationMessage.system(content));
    }

    public Session addUserMessage(String content) {
        return addMessage(ConversationMessage.userText(content));
    }

    public Session addAssistantMessage(String content) {
        return addMessage(ConversationMessage.assistant(List.of(new TextBlock(content))));
    }

    public Session recordToolCall(String toolName, String input) {
        String toolId = "tool-" + UUID.randomUUID();
        ToolUseBlock toolUse = new ToolUseBlock(toolId, toolName, input);
        ConversationMessage msg = new ConversationMessage(
            MessageRole.ASSISTANT, List.of(toolUse), null, Instant.now()
        );
        return addMessage(msg);
    }

    public Session recordToolResult(String toolUseId, String toolName, String output, boolean isError) {
        return addMessage(ConversationMessage.toolResult(toolUseId, toolName, output, isError));
    }

    public Session putMemory(String key, Object value) {
        Map<String, Object> newMemory = new HashMap<>(this.workingMemory);
        newMemory.put(key, value);
        return new Session(version, sessionId, createdAt, Instant.now(), Instant.now(),
                          messages, newMemory, executionLog, cumulativeUsage,
                          latestInferencePolicy, latestResponseMode);
    }

    public Optional<Object> getMemory(String key) {
        return Optional.ofNullable(workingMemory.get(key));
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getMemory(String key, Class<T> type) {
        return getMemory(key).filter(type::isInstance).map(type::cast);
    }

    public Session removeMemory(String key) {
        Map<String, Object> newMemory = new HashMap<>(this.workingMemory);
        newMemory.remove(key);
        return new Session(version, sessionId, createdAt, Instant.now(), Instant.now(),
                          messages, newMemory, executionLog, cumulativeUsage,
                          latestInferencePolicy, latestResponseMode);
    }

    public Session addExecutionLog(String content) {
        List<String> newLog = new ArrayList<>(this.executionLog);
        newLog.add(content);
        return new Session(version, sessionId, createdAt, Instant.now(), Instant.now(),
                          messages, workingMemory, newLog, cumulativeUsage,
                          latestInferencePolicy, latestResponseMode);
    }

    public Session updateCumulativeUsage(TokenUsage usage) {
        return new Session(version, sessionId, createdAt, Instant.now(), Instant.now(),
                          messages, workingMemory, executionLog, cumulativeUsage.add(usage),
                          latestInferencePolicy, latestResponseMode);
    }

    public boolean isEmpty() {
        return messages.isEmpty() && workingMemory.isEmpty() && executionLog.isEmpty();
    }

    public Session clear() {
        return new Session(version, sessionId, createdAt, Instant.now(), Instant.now(),
                          new ArrayList<>(), new HashMap<>(), new ArrayList<>(), TokenUsage.zero(),
                          null, null);
    }

    public int estimateTokens() {
        return messages.stream()
            .mapToInt(msg -> msg.blocks().stream()
                .mapToInt(block -> switch (block) {
                    case TextBlock t -> t.text() != null ? t.text().length() / 4 + 1 : 0;
                    case ToolUseBlock tu -> (tu.name().length() + tu.input().length()) / 4 + 1;
                    case ToolResultBlock tr -> (tr.toolName().length() + tr.output().length()) / 4 + 1;
                })
                .sum())
            .sum();
    }

    public Session withMessages(List<ConversationMessage> newMessages) {
        return new Session(version, sessionId, createdAt, Instant.now(), Instant.now(),
                          new ArrayList<>(newMessages), workingMemory, executionLog, cumulativeUsage,
                          latestInferencePolicy, latestResponseMode);
    }

    public Map<String, Object> getAllMemory() {
        return Map.copyOf(workingMemory);
    }

    public List<String> getExecutionLogList() {
        return List.copyOf(executionLog);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SessionTest -pl openmanus-spring-ai-alibaba`
Expected: PASS (16 tests)

- [ ] **Step 5: Commit**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/model/session/Session.java openmanus-spring-ai-alibaba/src/test/java/com/openmanus/saa/model/session/SessionTest.java
git commit -m "feat: add unified Session model"
```

---

## Task 7: SessionCompactor (Claw-style)

**Files:**
- Modify: `src/main/java/com/openmanus/saa/service/session/SessionCompactor.java`
- Create: `src/test/java/com/openmanus/saa/service/session/SessionCompactorTest.java`

- [ ] **Step 1: Read existing SessionCompactor**

Read: `src/main/java/com/openmanus/saa/service/session/SessionCompactor.java`
Note current implementation and usages.

- [ ] **Step 2: Write the failing test**

```java
// src/test/java/com/openmanus/saa/service/session/SessionCompactorTest.java
package com.openmanus.saa.service.session;

import com.openmanus.saa.model.session.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SessionCompactorTest {

    private SessionCompactor compactor;

    @BeforeEach
    void setUp() {
        compactor = new SessionCompactor();
    }

    @Test
    void shouldNotCompactSmallSession() {
        Session session = new Session("test");
        session = session.addMessage(ConversationMessage.userText("hello"));
        
        CompactionConfig config = new CompactionConfig(4, 100);
        boolean shouldCompact = compactor.shouldCompact(session, config);
        
        assertThat(shouldCompact).isFalse();
    }

    @Test
    void shouldCompactLargeSession() {
        Session session = new Session("test");
        // Add many large messages to exceed threshold
        for (int i = 0; i < 10; i++) {
            session = session.addMessage(ConversationMessage.userText("x".repeat(100)));
        }
        
        CompactionConfig config = new CompactionConfig(2, 10);
        boolean shouldCompact = compactor.shouldCompact(session, config);
        
        assertThat(shouldCompact).isTrue();
    }

    @Test
    void compactSessionPreservesRecentMessages() {
        Session session = new Session("test");
        session = session.addMessage(ConversationMessage.userText("old message 1"));
        session = session.addMessage(ConversationMessage.userText("old message 2"));
        session = session.addMessage(ConversationMessage.userText("recent message 1"));
        session = session.addMessage(ConversationMessage.userText("recent message 2"));
        
        CompactionConfig config = new CompactionConfig(2, 1);
        CompactionResult result = compactor.compactSession(session, config);
        
        assertThat(result.removedMessageCount()).isEqualTo(2);
        assertThat(result.compactedSession().messages()).hasSize(3); // 1 system + 2 recent
        assertThat(result.compactedSession().messages().get(0).role()).isEqualTo(MessageRole.SYSTEM);
    }

    @Test
    void compactSessionGeneratesSummary() {
        Session session = new Session("test");
        session = session.addMessage(ConversationMessage.userText("Update rust/crates/runtime/src/compact.rs"));
        session = session.addMessage(ConversationMessage.userText("Add tests for compaction"));
        
        CompactionConfig config = new CompactionConfig(1, 1);
        CompactionResult result = compactor.compactSession(session, config);
        
        assertThat(result.summary()).isNotEmpty();
        assertThat(result.summary()).contains("Scope:");
        assertThat(result.summary()).contains("Key timeline:");
    }

    @Test
    void compactSessionExtractsKeyFiles() {
        Session session = new Session("test");
        session = session.addMessage(ConversationMessage.userText(
            "Update rust/crates/runtime/src/compact.rs and pom.xml"
        ));
        
        CompactionConfig config = new CompactionConfig(1, 1);
        CompactionResult result = compactor.compactSession(session, config);
        
        assertThat(result.summary()).contains("compact.rs");
    }

    @Test
    void compactSessionInfersPendingWork() {
        Session session = new Session("test");
        session = session.addMessage(ConversationMessage.userText("done"));
        session = session.addMessage(ConversationMessage.userText(
            "Next: update tests and follow up on remaining tasks"
        ));
        
        CompactionConfig config = new CompactionConfig(1, 1);
        CompactionResult result = compactor.compactSession(session, config);
        
        assertThat(result.summary()).contains("Pending work:");
    }

    @Test
    void formatCompactSummaryRemovesAnalysisTag() {
        String summary = "<analysis>scratch</analysis>\n<summary>Kept work</summary>";
        
        String formatted = compactor.formatCompactSummary(summary);
        
        assertThat(formatted).doesNotContain("<analysis>");
        assertThat(formatted).contains("Summary:");
        assertThat(formatted).contains("Kept work");
    }

    @Test
    void getCompactContinuationMessageIncludesPreamble() {
        String summary = "Test summary";
        
        String continuation = compactor.getCompactContinuationMessage(summary, true, true);
        
        assertThat(continuation).contains("This session is being continued");
        assertThat(continuation).contains("Test summary");
        assertThat(continuation).contains("Recent messages are preserved");
        assertThat(continuation).contains("Continue the conversation");
    }

    @Test
    void estimateSessionTokensCalculatesTotal() {
        Session session = new Session("test");
        session = session.addMessage(ConversationMessage.userText("Hello World")); // 11 chars
        session = session.addMessage(ConversationMessage.userText("Test message")); // 12 chars
        
        int tokens = compactor.estimateSessionTokens(session);
        
        assertThat(tokens).isGreaterThan(0);
    }

    @Test
    void multipleCompactionsPreservePreviousSummary() {
        // First compaction
        Session session = new Session("test");
        session = session.addMessage(ConversationMessage.userText("x".repeat(200)));
        session = session.addMessage(ConversationMessage.userText("y".repeat(200)));
        
        CompactionConfig config = new CompactionConfig(1, 1);
        CompactionResult first = compactor.compactSession(session, config);
        
        // Add more messages
        Session secondSession = first.compactedSession();
        secondSession = secondSession.addMessage(ConversationMessage.userText("z".repeat(200)));
        secondSession = secondSession.addMessage(ConversationMessage.userText("w".repeat(200)));
        
        // Second compaction
        CompactionResult second = compactor.compactSession(secondSession, config);
        
        assertThat(second.summary()).contains("Previously compacted context:");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -Dtest=SessionCompactorTest -pl openmanus-spring-ai-alibaba`
Expected: FAIL (SessionCompactor needs to be rewritten)

- [ ] **Step 4: Rewrite SessionCompactor**

```java
// src/main/java/com/openmanus/saa/service/session/SessionCompactor.java
package com.openmanus.saa.service.session;

import com.openmanus.saa.model.session.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class SessionCompactor {

    private static final String COMPACT_CONTINUATION_PREAMBLE =
        "This session is being continued from a previous conversation that ran out of context. " +
        "The summary below covers the earlier portion of the conversation.\n\n";

    private static final String COMPACT_RECENT_MESSAGES_NOTE =
        "Recent messages are preserved verbatim.";

    private static final String COMPACT_DIRECT_RESUME_INSTRUCTION =
        "Continue the conversation from where it left off without asking the user any " +
        "further questions. Resume directly — do not acknowledge the summary, do not recap " +
        "what was happening, and do not preface with continuation text.";

    private static final List<String> INTERESTING_EXTENSIONS = List.of(
        "rs", "ts", "tsx", "js", "json", "md", "java", "xml", "yml", "yaml", "properties", "sql"
    );

    private static final List<String> PENDING_KEYWORDS = List.of(
        "todo", "next", "pending", "follow up", "remaining",
        "待办", "下一步", "未完成", "后续"
    );

    public int estimateSessionTokens(Session session) {
        return session.messages().stream()
            .mapToInt(this::estimateMessageTokens)
            .sum();
    }

    public boolean shouldCompact(Session session, CompactionConfig config) {
        int start = compactedSummaryPrefixLen(session);
        List<ConversationMessage> compactable = session.messages().subList(start, session.messages().size());

        if (compactable.size() <= config.preserveRecentMessages()) {
            return false;
        }

        int compactableTokens = compactable.stream()
            .mapToInt(this::estimateMessageTokens)
            .sum();

        return compactableTokens >= config.maxEstimatedTokens();
    }

    public CompactionResult compactSession(Session session, CompactionConfig config) {
        if (!shouldCompact(session, config)) {
            return CompactionResult.unchanged(session);
        }

        Optional<String> existingSummary = session.messages().stream()
            .findFirst()
            .flatMap(this::extractExistingCompactedSummary);

        int compactedPrefixLen = existingSummary.map(s -> 1).orElse(0);
        int keepFrom = session.messages().size() - config.preserveRecentMessages();

        List<ConversationMessage> removed = session.messages().subList(compactedPrefixLen, keepFrom);
        List<ConversationMessage> preserved = new ArrayList<>(session.messages().subList(keepFrom, session.messages().size()));

        String newSummary = summarizeMessages(removed);
        String summary = mergeCompactSummaries(existingSummary.orElse(null), newSummary);
        String formattedSummary = formatCompactSummary(summary);
        String continuation = getCompactContinuationMessage(summary, true, !preserved.isEmpty());

        List<ConversationMessage> compactedMessages = new ArrayList<>();
        compactedMessages.add(ConversationMessage.system(continuation));
        compactedMessages.addAll(preserved);

        Session compactedSession = session.withMessages(compactedMessages);

        return new CompactionResult(summary, formattedSummary, compactedSession, removed.size());
    }

    public String formatCompactSummary(String summary) {
        String withoutAnalysis = stripTagBlock(summary, "analysis");
        String formatted;
        Optional<String> content = extractTagBlock(withoutAnalysis, "summary");
        if (content.isPresent()) {
            formatted = withoutAnalysis.replace(
                "<summary>" + content.get() + "</summary>",
                "Summary:\n" + content.get().trim()
            );
        } else {
            formatted = withoutAnalysis;
        }
        return collapseBlankLines(formatted).trim();
    }

    public String getCompactContinuationMessage(String summary, boolean suppressFollowUpQuestions, boolean recentMessagesPreserved) {
        StringBuilder sb = new StringBuilder();
        sb.append(COMPACT_CONTINUATION_PREAMBLE);
        sb.append(formatCompactSummary(summary));

        if (recentMessagesPreserved) {
            sb.append("\n\n");
            sb.append(COMPACT_RECENT_MESSAGES_NOTE);
        }

        if (suppressFollowUpQuestions) {
            sb.append('\n');
            sb.append(COMPACT_DIRECT_RESUME_INSTRUCTION);
        }

        return sb.toString();
    }

    private int compactedSummaryPrefixLen(Session session) {
        if (session.messages().isEmpty()) return 0;
        return session.messages().getFirst().role() == MessageRole.SYSTEM &&
               extractExistingCompactedSummary(session.messages().getFirst()).isPresent() ? 1 : 0;
    }

    private String summarizeMessages(List<ConversationMessage> messages) {
        long userCount = messages.stream().filter(m -> m.role() == MessageRole.USER).count();
        long assistantCount = messages.stream().filter(m -> m.role() == MessageRole.ASSISTANT).count();
        long toolCount = messages.stream().filter(m -> m.role() == MessageRole.TOOL).count();

        List<String> toolNames = messages.stream()
            .flatMap(m -> m.blocks().stream())
            .flatMap(block -> switch (block) {
                case ToolUseBlock tu -> Stream.of(tu.name());
                case ToolResultBlock tr -> Stream.of(tr.toolName());
                default -> Stream.empty();
            })
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        List<String> recentUserRequests = collectRecentRoleSummaries(messages, MessageRole.USER, 3);
        List<String> pendingWork = inferPendingWork(messages);
        List<String> keyFiles = collectKeyFiles(messages);
        Optional<String> currentWork = inferCurrentWork(messages);

        List<String> lines = new ArrayList<>();
        lines.add("<summary>");
        lines.add("Conversation summary:");
        lines.add(String.format("- Scope: %d earlier messages compacted (user=%d, assistant=%d, tool=%d).",
            messages.size(), userCount, assistantCount, toolCount));

        if (!toolNames.isEmpty()) {
            lines.add("- Tools mentioned: " + String.join(", ", toolNames) + ".");
        }

        if (!recentUserRequests.isEmpty()) {
            lines.add("- Recent user requests:");
            recentUserRequests.forEach(r -> lines.add("  - " + r));
        }

        if (!pendingWork.isEmpty()) {
            lines.add("- Pending work:");
            pendingWork.forEach(p -> lines.add("  - " + p));
        }

        if (!keyFiles.isEmpty()) {
            lines.add("- Key files referenced: " + String.join(", ", keyFiles) + ".");
        }

        currentWork.ifPresent(w -> lines.add("- Current work: " + w));

        lines.add("- Key timeline:");
        for (ConversationMessage message : messages) {
            String role = message.role().name().toLowerCase();
            String content = message.blocks().stream()
                .map(this::summarizeBlock)
                .collect(Collectors.joining(" | "));
            lines.add(String.format("  - %s: %s", role, content));
        }
        lines.add("</summary>");

        return String.join("\n", lines);
    }

    private String mergeCompactSummaries(String existingSummary, String newSummary) {
        if (existingSummary == null) {
            return newSummary;
        }

        List<String> previousHighlights = extractSummaryHighlights(existingSummary);
        String newFormatted = formatCompactSummary(newSummary);
        List<String> newHighlights = extractSummaryHighlights(newFormatted);
        List<String> newTimeline = extractSummaryTimeline(newFormatted);

        List<String> lines = new ArrayList<>();
        lines.add("<summary>");
        lines.add("Conversation summary:");

        if (!previousHighlights.isEmpty()) {
            lines.add("- Previously compacted context:");
            previousHighlights.forEach(h -> lines.add("  " + h));
        }

        if (!newHighlights.isEmpty()) {
            lines.add("- Newly compacted context:");
            newHighlights.forEach(h -> lines.add("  " + h));
        }

        if (!newTimeline.isEmpty()) {
            lines.add("- Key timeline:");
            newTimeline.forEach(t -> lines.add("  " + t));
        }

        lines.add("</summary>");
        return String.join("\n", lines);
    }

    private String summarizeBlock(ContentBlock block) {
        String raw = switch (block) {
            case TextBlock t -> t.text();
            case ToolUseBlock tu -> String.format("tool_use %s(%s)", tu.name(), truncateSummary(tu.input(), 160));
            case ToolResultBlock tr -> String.format("tool_result %s: %s%s",
                tr.toolName(), tr.isError() ? "error " : "", truncateSummary(tr.output(), 160));
        };
        return truncateSummary(raw, 160);
    }

    private List<String> collectRecentRoleSummaries(List<ConversationMessage> messages, MessageRole role, int limit) {
        List<String> results = new ArrayList<>();
        for (ConversationMessage msg : messages) {
            if (msg.role() == role) {
                firstTextBlock(msg).ifPresent(t -> results.add(truncateSummary(t, 160)));
                if (results.size() >= limit) break;
            }
        }
        Collections.reverse(results);
        return results;
    }

    private List<String> inferPendingWork(List<ConversationMessage> messages) {
        List<String> results = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            String text = firstTextBlock(messages.get(i)).orElse(null);
            if (text != null) {
                String lowered = text.toLowerCase();
                boolean matches = PENDING_KEYWORDS.stream().anyMatch(lowered::contains);
                if (matches) {
                    results.add(truncateSummary(text, 160));
                    if (results.size() >= 3) break;
                }
            }
        }
        Collections.reverse(results);
        return results;
    }

    private List<String> collectKeyFiles(List<ConversationMessage> messages) {
        List<String> files = messages.stream()
            .flatMap(m -> m.blocks().stream())
            .flatMap(block -> switch (block) {
                case TextBlock t -> Stream.of(t.text());
                case ToolUseBlock tu -> Stream.of(tu.input());
                case ToolResultBlock tr -> Stream.of(tr.output());
            })
            .flatMap(this::extractFileCandidates)
            .distinct()
            .limit(8)
            .collect(Collectors.toList());
        return files;
    }

    private Stream<String> extractFileCandidates(String content) {
        if (content == null) return Stream.empty();
        return Arrays.stream(content.split("\\s+"))
            .map(token -> token.replaceAll("[,\\.:;()\"'`]", ""))
            .filter(candidate -> candidate.contains("/"))
            .filter(this::hasInterestingExtension);
    }

    private boolean hasInterestingExtension(String candidate) {
        int dotIndex = candidate.lastIndexOf('.');
        if (dotIndex < 0) return false;
        String ext = candidate.substring(dotIndex + 1).toLowerCase();
        return INTERESTING_EXTENSIONS.contains(ext);
    }

    private Optional<String> inferCurrentWork(List<ConversationMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            String text = firstTextBlock(messages.get(i)).orElse(null);
            if (text != null && !text.trim().isEmpty()) {
                return Optional.of(truncateSummary(text, 200));
            }
        }
        return Optional.empty();
    }

    private Optional<String> firstTextBlock(ConversationMessage message) {
        return message.blocks().stream()
            .filter(block -> block instanceof TextBlock)
            .map(block -> ((TextBlock) block).text())
            .filter(text -> text != null && !text.trim().isEmpty())
            .findFirst();
    }

    private int estimateMessageTokens(ConversationMessage message) {
        return message.blocks().stream()
            .mapToInt(block -> switch (block) {
                case TextBlock t -> (t.text() != null ? t.text().length() : 0) / 4 + 1;
                case ToolUseBlock tu -> (tu.name().length() + tu.input().length()) / 4 + 1;
                case ToolResultBlock tr -> (tr.toolName().length() + tr.output().length()) / 4 + 1;
            })
            .sum();
    }

    private Optional<String> extractExistingCompactedSummary(ConversationMessage message) {
        if (message.role() != MessageRole.SYSTEM) return Optional.empty();
        String text = firstTextBlock(message).orElse(null);
        if (text == null || !text.startsWith(COMPACT_CONTINUATION_PREAMBLE)) return Optional.empty();
        String summary = text.substring(COMPACT_CONTINUATION_PREAMBLE.length());
        int recentIdx = summary.indexOf("\n\n" + COMPACT_RECENT_MESSAGES_NOTE);
        if (recentIdx >= 0) summary = summary.substring(0, recentIdx);
        int resumeIdx = summary.indexOf("\n" + COMPACT_DIRECT_RESUME_INSTRUCTION);
        if (resumeIdx >= 0) summary = summary.substring(0, resumeIdx);
        return Optional.of(summary.trim());
    }

    private List<String> extractSummaryHighlights(String summary) {
        List<String> lines = new ArrayList<>();
        boolean inTimeline = false;
        for (String line : formatCompactSummary(summary).split("\n")) {
            String trimmed = line.trimEnd();
            if (trimmed.isEmpty() || trimmed.equals("Summary:") || trimmed.equals("Conversation summary:")) continue;
            if (trimmed.equals("- Key timeline:")) { inTimeline = true; continue; }
            if (inTimeline) continue;
            lines.add(trimmed);
        }
        return lines;
    }

    private List<String> extractSummaryTimeline(String summary) {
        List<String> lines = new ArrayList<>();
        boolean inTimeline = false;
        for (String line : formatCompactSummary(summary).split("\n")) {
            String trimmed = line.trimEnd();
            if (trimmed.equals("- Key timeline:")) { inTimeline = true; continue; }
            if (!inTimeline) continue;
            if (trimmed.isEmpty()) break;
            lines.add(trimmed);
        }
        return lines;
    }

    private Optional<String> extractTagBlock(String content, String tag) {
        String start = "<" + tag + ">";
        String end = "</" + tag + ">";
        int startIdx = content.indexOf(start);
        if (startIdx < 0) return Optional.empty();
        startIdx += start.length();
        int endIdx = content.indexOf(end, startIdx);
        if (endIdx < 0) return Optional.empty();
        return Optional.of(content.substring(startIdx, endIdx));
    }

    private String stripTagBlock(String content, String tag) {
        String start = "<" + tag + ">";
        String end = "</" + tag + ">";
        int startIdx = content.indexOf(start);
        int endIdx = content.indexOf(end);
        if (startIdx >= 0 && endIdx >= 0) {
            endIdx += end.length();
            return content.substring(0, startIdx) + content.substring(endIdx);
        }
        return content;
    }

    private String collapseBlankLines(String content) {
        StringBuilder result = new StringBuilder();
        boolean lastBlank = false;
        for (String line : content.split("\n")) {
            boolean isBlank = line.trim().isEmpty();
            if (isBlank && lastBlank) continue;
            result.append(line).append('\n');
            lastBlank = isBlank;
        }
        return result.toString();
    }

    private String truncateSummary(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) return content;
        return content.substring(0, maxChars) + "…";
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=SessionCompactorTest -pl openmanus-spring-ai-alibaba`
Expected: PASS (11 tests)

- [ ] **Step 6: Commit**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/session/SessionCompactor.java openmanus-spring-ai-alibaba/src/test/java/com/openmanus/saa/service/session/SessionCompactorTest.java
git commit -m "feat: implement Claw-style SessionCompactor with continuation message"
```

---

## Task 8: SessionToSpringAIConverter

**Files:**
- Create: `src/main/java/com/openmanus/saa/service/session/SessionToSpringAIConverter.java`
- Create: `src/test/java/com/openmanus/saa/service/session/SessionToSpringAIConverterTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/openmanus/saa/service/session/SessionToSpringAIConverterTest.java
package com.openmanus.saa.service.session;

import com.openmanus.saa.model.session.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SessionToSpringAIConverterTest {

    private SessionToSpringAIConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SessionToSpringAIConverter();
    }

    @Test
    void convertSystemMessage() {
        Session session = new Session("test");
        session = session.addSystemMessage("You are a helpful assistant");

        List<Message> result = converter.convert(session);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(result.get(0).getContent()).contains("helpful assistant");
    }

    @Test
    void convertUserMessage() {
        Session session = new Session("test");
        session = session.addUserMessage("Hello");

        List<Message> result = converter.convert(session);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(UserMessage.class);
        assertThat(result.get(0).getContent()).contains("Hello");
    }

    @Test
    void convertAssistantMessage() {
        Session session = new Session("test");
        session = session.addAssistantMessage("I can help you");

        List<Message> result = converter.convert(session);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(AssistantMessage.class);
        assertThat(result.get(0).getContent()).contains("I can help you");
    }

    @Test
    void convertToolUseBlock() {
        Session session = new Session("test");
        session = session.addMessage(ConversationMessage.assistantWithUsage(
            List.of(new ToolUseBlock("id-1", "bash", "echo hello")),
            null
        ));

        List<Message> result = converter.convert(session);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).contains("bash");
        assertThat(result.get(0).getContent()).contains("echo hello");
    }

    @Test
    void convertToolResultBlock() {
        Session session = new Session("test");
        session = session.addMessage(ConversationMessage.toolResult("id-1", "bash", "hello world", false));

        List<Message> result = converter.convert(session);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(UserMessage.class); // Tool results as UserMessage
        assertThat(result.get(0).getContent()).contains("bash");
        assertThat(result.get(0).getContent()).contains("hello world");
    }

    @Test
    void convertToolResultWithError() {
        Session session = new Session("test");
        session = session.addMessage(ConversationMessage.toolResult("id-1", "bash", "command failed", true));

        List<Message> result = converter.convert(session);

        assertThat(result.get(0).getContent()).contains("ERROR");
        assertThat(result.get(0).getContent()).contains("command failed");
    }

    @Test
    void convertMixedMessages() {
        Session session = new Session("test");
        session = session.addSystemMessage("System");
        session = session.addUserMessage("User");
        session = session.addAssistantMessage("Assistant");

        List<Message> result = converter.convert(session);

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(result.get(1)).isInstanceOf(UserMessage.class);
        assertThat(result.get(2)).isInstanceOf(AssistantMessage.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SessionToSpringAIConverterTest -pl openmanus-spring-ai-alibaba`
Expected: FAIL with "SessionToSpringAIConverter cannot be resolved"

- [ ] **Step 3: Write SessionToSpringAIConverter**

```java
// src/main/java/com/openmanus/saa/service/session/SessionToSpringAIConverter.java
package com.openmanus.saa.service.session;

import com.openmanus.saa.model.session.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SessionToSpringAIConverter {

    public List<Message> convert(Session session) {
        return session.messages().stream()
            .map(this::convertMessage)
            .collect(Collectors.toList());
    }

    private Message convertMessage(ConversationMessage msg) {
        return switch (msg.role()) {
            case SYSTEM -> new SystemMessage(convertBlocks(msg.blocks()));
            case USER -> new UserMessage(convertBlocks(msg.blocks()));
            case ASSISTANT -> new AssistantMessage(convertBlocks(msg.blocks()));
            case TOOL -> convertToolMessage(msg);
        };
    }

    private String convertBlocks(List<ContentBlock> blocks) {
        return blocks.stream()
            .map(block -> switch (block) {
                case TextBlock t -> t.text();
                case ToolUseBlock tu -> formatToolUse(tu);
                case ToolResultBlock tr -> formatToolResult(tr);
            })
            .collect(Collectors.joining("\n"));
    }

    private Message convertToolMessage(ConversationMessage msg) {
        String content = msg.blocks().stream()
            .map(block -> {
                if (block instanceof ToolResultBlock tr) {
                    return formatToolResult(tr);
                }
                return block.toSummary();
            })
            .collect(Collectors.joining("\n"));
        return new UserMessage(content);
    }

    private String formatToolUse(ToolUseBlock tu) {
        return String.format("[Tool Use: %s, Input: %s]", tu.name(), tu.input());
    }

    private String formatToolResult(ToolResultBlock tr) {
        return String.format("[Tool Result: %s%s, Output: %s]",
            tr.isError() ? "ERROR: " : "",
            tr.toolName(),
            tr.output());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SessionToSpringAIConverterTest -pl openmanus-spring-ai-alibaba`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/session/SessionToSpringAIConverter.java openmanus-spring-ai-alibaba/src/test/java/com/openmanus/saa/service/session/SessionToSpringAIConverterTest.java
git commit -m "feat: add SessionToSpringAIConverter for Spring AI integration"
```

---

## Task 9: TokenUsageExtractor

**Files:**
- Create: `src/main/java/com/openmanus/saa/service/session/TokenUsageExtractor.java`
- Create: `src/test/java/com/openmanus/saa/service/session/TokenUsageExtractorTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/openmanus/saa/service/session/TokenUsageExtractorTest.java
package com.openmanus.saa.service.session;

import com.openmanus.saa.model.session.TokenUsage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
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
        when(usage.getPromptTokens()).thenReturn(100L);
        when(usage.getGenerationTokens()).thenReturn(50L);
        when(response.getMetadata()).thenReturn(mock());
        when(response.getMetadata().getUsage()).thenReturn(usage);

        TokenUsage result = extractor.extract(response);

        assertThat(result).isEqualTo(new TokenUsage(100, 50, 0, 0));
    }

    @Test
    void extractHandlesNullTokenCounts() {
        ChatResponse response = mock(ChatResponse.class);
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(null);
        when(usage.getGenerationTokens()).thenReturn(null);
        when(response.getMetadata()).thenReturn(mock());
        when(response.getMetadata().getUsage()).thenReturn(usage);

        TokenUsage result = extractor.extract(response);

        assertThat(result).isEqualTo(new TokenUsage(0, 0, 0, 0));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=TokenUsageExtractorTest -pl openmanus-spring-ai-alibaba`
Expected: FAIL with "TokenUsageExtractor cannot be resolved"

- [ ] **Step 3: Write TokenUsageExtractor**

```java
// src/main/java/com/openmanus/saa/service/session/TokenUsageExtractor.java
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
            usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0,
            usage.getGenerationTokens() != null ? usage.getGenerationTokens().intValue() : 0,
            0,
            0
        );
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=TokenUsageExtractorTest -pl openmanus-spring-ai-alibaba`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/session/TokenUsageExtractor.java openmanus-spring-ai-alibaba/src/test/java/com/openmanus/saa/service/session/TokenUsageExtractorTest.java
git commit -m "feat: add TokenUsageExtractor for ChatResponse"
```

---

## Task 10: SessionSerializer (Jackson)

**Files:**
- Create: `src/main/java/com/openmanus/saa/service/session/SessionSerializer.java`
- Create: `src/test/java/com/openmanus/saa/service/session/SessionSerializerTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/openmanus/saa/service/session/SessionSerializerTest.java
package com.openmanus.saa.service.session;

import com.openmanus.saa.model.session.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SessionSerializerTest {

    private SessionSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new SessionSerializer();
    }

    @Test
    void serializeAndDeserializeEmptySession() {
        Session session = new Session("test-session");

        String json = serializer.toJson(session);
        Session deserialized = serializer.fromJson(json);

        assertThat(deserialized.sessionId()).isEqualTo("test-session");
        assertThat(deserialized.version()).isEqualTo(1);
        assertThat(deserialized.messages()).isEmpty();
    }

    @Test
    void serializeAndDeserializeWithMessages() {
        Session session = new Session("test-session");
        session = session.addMessage(ConversationMessage.userText("Hello"));
        session = session.addMessage(ConversationMessage.assistant(List.of(new TextBlock("Hi there"))));

        String json = serializer.toJson(session);
        Session deserialized = serializer.fromJson(json);

        assertThat(deserialized.messages()).hasSize(2);
        assertThat(deserialized.messages().get(0).role()).isEqualTo(MessageRole.USER);
        assertThat(deserialized.messages().get(1).role()).isEqualTo(MessageRole.ASSISTANT);
    }

    @Test
    void serializeAndDeserializeWithToolBlocks() {
        Session session = new Session("test-session");
        session = session.addMessage(ConversationMessage.assistantWithUsage(
            List.of(new ToolUseBlock("id-1", "bash", "echo hello")),
            new TokenUsage(100, 50, 10, 5)
        ));
        session = session.addMessage(ConversationMessage.toolResult("id-1", "bash", "hello", false));

        String json = serializer.toJson(session);
        Session deserialized = serializer.fromJson(json);

        assertThat(deserialized.messages()).hasSize(2);
        assertThat(deserialized.messages().get(0).blocks().get(0)).isInstanceOf(ToolUseBlock.class);
        assertThat(deserialized.messages().get(1).blocks().get(0)).isInstanceOf(ToolResultBlock.class);
        assertThat(deserialized.messages().get(0).usage()).isNotNull();
    }

    @Test
    void serializeAndDeserializeWithWorkingMemory() {
        Session session = new Session("test-session");
        session = session.putMemory("key1", "value1");
        session = session.putMemory("key2", 42);

        String json = serializer.toJson(session);
        Session deserialized = serializer.fromJson(json);

        assertThat(deserialized.getMemory("key1")).contains("value1");
        assertThat(deserialized.getMemory("key2")).contains(42);
    }

    @Test
    void serializeAndDeserializeWithCumulativeUsage() {
        Session session = new Session("test-session");
        session = session.updateCumulativeUsage(new TokenUsage(100, 50, 10, 5));

        String json = serializer.toJson(session);
        Session deserialized = serializer.fromJson(json);

        assertThat(deserialized.cumulativeUsage()).isEqualTo(new TokenUsage(100, 50, 10, 5));
    }

    @Test
    void serializeAndDeserializeWithExecutionLog() {
        Session session = new Session("test-session");
        session = session.addExecutionLog("Step 1 executed");
        session = session.addExecutionLog("Step 2 executed");

        String json = serializer.toJson(session);
        Session deserialized = serializer.fromJson(json);

        assertThat(deserialized.getExecutionLogList()).hasSize(2);
        assertThat(deserialized.getExecutionLogList()).contains("Step 1 executed", "Step 2 executed");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SessionSerializerTest -pl openmanus-spring-ai-alibaba`
Expected: FAIL with "SessionSerializer cannot be resolved"

- [ ] **Step 3: Write SessionSerializer**

```java
// src/main/java/com/openmanus/saa/service/session/SessionSerializer.java
package com.openmanus.saa.service.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.session.Session;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class SessionSerializer {

    private final ObjectMapper objectMapper;

    public SessionSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Session session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize Session", e);
        }
    }

    public Session fromJson(String json) {
        try {
            return objectMapper.readValue(json, Session.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize Session", e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SessionSerializerTest -pl openmanus-spring-ai-alibaba`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/session/SessionSerializer.java openmanus-spring-ai-alibaba/src/test/java/com/openmanus/saa/service/session/SessionSerializerTest.java
git commit -m "feat: add SessionSerializer with Jackson"
```

---

## Task 11: SessionConfig Update

**Files:**
- Modify: `src/main/java/com/openmanus/saa/config/SessionConfig.java`

- [ ] **Step 1: Read existing SessionConfig**

Read: `src/main/java/com/openmanus/saa/config/SessionConfig.java`

- [ ] **Step 2: Update SessionConfig**

```java
// src/main/java/com/openmanus/saa/config/SessionConfig.java
package com.openmanus.saa.config;

import com.openmanus.saa.model.session.CompactionConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "openmanus.session")
public class SessionConfig {

    public enum StorageType {
        MEMORY, MYSQL, JSON_FILE
    }

    private StorageType storage = StorageType.MEMORY;
    private Duration sessionTtl = Duration.ofMinutes(30);
    private Duration cleanupInterval = Duration.ofMinutes(1);
    private boolean compactionEnabled = true;
    private CompactionConfig compaction = CompactionConfig.DEFAULT;

    // Getters and setters
    public StorageType getStorage() { return storage; }
    public void setStorage(StorageType storage) { this.storage = storage; }

    public Duration getSessionTtl() { return sessionTtl; }
    public void setSessionTtl(Duration sessionTtl) { this.sessionTtl = sessionTtl; }

    public Duration getCleanupInterval() { return cleanupInterval; }
    public void setCleanupInterval(Duration cleanupInterval) { this.cleanupInterval = cleanupInterval; }

    public boolean isCompactionEnabled() { return compactionEnabled; }
    public void setCompactionEnabled(boolean compactionEnabled) { this.compactionEnabled = compactionEnabled; }

    public CompactionConfig getCompaction() { return compaction; }
    public void setCompaction(CompactionConfig compaction) { this.compaction = compaction; }

    public CompactionConfig getCompactionConfig() { return compaction; }
}
```

- [ ] **Step 3: Commit**

```bash
git add openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/config/SessionConfig.java
git commit -m "refactor: update SessionConfig with compaction settings"
```

---

## Task 12: Full Test Suite Run

**Files:**
- Run all tests to verify no regressions

- [ ] **Step 1: Run full test suite**

Run: `mvn test -pl openmanus-spring-ai-alibaba`
Expected: All tests pass

- [ ] **Step 2: Verify test count**

Run: `mvn test -pl openmanus-spring-ai-alibaba 2>&1 | grep "Tests run:"`
Expected: Shows total test count including new tests

- [ ] **Step 3: Commit if needed**

If any fixes were required:
```bash
git add -A
git commit -m "fix: resolve test failures"
```

---

## Summary

### New Files Created (18)

| File | Type |
|------|------|
| `model/session/TokenUsage.java` | Record |
| `model/session/BlockType.java` | Enum |
| `model/session/TextBlock.java` | Record |
| `model/session/ToolUseBlock.java` | Record |
| `model/session/ToolResultBlock.java` | Record |
| `model/session/MessageRole.java` | Enum |
| `model/session/Session.java` | Record |
| `service/session/SessionToSpringAIConverter.java` | Component |
| `service/session/TokenUsageExtractor.java` | Component |
| `service/session/SessionSerializer.java` | Component |
| `test/model/session/TokenUsageTest.java` | Test |
| `test/model/session/ContentBlockTest.java` | Test |
| `test/model/session/ConversationMessageTest.java` | Test |
| `test/model/session/SessionTest.java` | Test |
| `test/service/session/SessionCompactorTest.java` | Test |
| `test/service/session/SessionToSpringAIConverterTest.java` | Test |
| `test/service/session/TokenUsageExtractorTest.java` | Test |
| `test/service/session/SessionSerializerTest.java` | Test |

### Files Modified (4)

| File | Changes |
|------|---------|
| `model/session/ConversationMessage.java` | Updated to use List<ContentBlock> + TokenUsage |
| `model/session/CompactionConfig.java` | Rewritten as record |
| `model/session/CompactionResult.java` | Rewritten as record |
| `service/session/SessionCompactor.java` | Rewritten with Claw-style logic |
| `config/SessionConfig.java` | Added compaction config |

### Files to Delete (after migration - future task)

| File | Reason |
|------|--------|
| `model/session/SessionState.java` | Merged into Session |
| `model/session/ConversationSession.java` | Merged into Session |

### Test Coverage

- TokenUsage: 4 tests
- ContentBlock: 7 tests
- ConversationMessage: 5 tests
- Session: 16 tests
- SessionCompactor: 11 tests
- SessionToSpringAIConverter: 7 tests
- TokenUsageExtractor: 5 tests
- SessionSerializer: 7 tests
- **Total: 62 new tests**

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-07-session-architecture-unification.md`.

Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
