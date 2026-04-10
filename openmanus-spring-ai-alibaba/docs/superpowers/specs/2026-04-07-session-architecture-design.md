# 会话架构统一设计文档

> **日期**: 2026-04-07
> **目标**: 参照 Claw Code (Rust) 的 Session 设计，统一当前项目的会话架构
> **范围**: 会话模型、消息结构、Token 追踪、会话压缩

---

## 一、概述

### 1.1 背景

当前项目存在两套并行的会话系统：
- `SessionState` - 用于持久化对话历史
- `ConversationSession` - 用于 Workflow 执行上下文

两套系统职责重叠，消息结构不统一，缺少 Token 使用量追踪，会话压缩机制简单。

### 1.2 目标

1. **统一 Session 模型** - 合并为单一的 `Session` 类
2. **引入 ContentBlock 抽象** - 统一 `Text`/`ToolUse`/`ToolResult` 处理
3. **完整 Token 追踪** - 每条消息携带使用量，支持累计统计
4. **智能会话压缩** - 实现 continuation message + 关键上下文保留

### 1.3 设计参考

- [Claw Code Session](D:/projects/claw-code/claw-code-main/claw-code-main/rust/crates/runtime/src/session.rs)
- [Claw Code Compact](D:/projects/claw-code/claw-code-main/claw-code-main/rust/crates/runtime/src/compact.rs)

---

## 二、核心数据结构

### 2.1 Session

统一的会话模型，替代原有的 `SessionState` 和 `ConversationSession`。

```java
package com.openmanus.saa.model.session;

import java.time.Instant;
import java.util.*;

public class Session {
    private int version;                                    // 版本号
    private final String sessionId;                         // 会话ID
    private final Instant createdAt;                        // 创建时间
    private Instant updatedAt;                              // 更新时间
    private Instant lastAccessedAt;                         // 最后访问时间（TTL）
    
    // 核心消息存储
    private final List<ConversationMessage> messages;       // 统一的消息列表
    
    // 扩展字段（保留原有功能）
    private final List<String> executionLog;                // 执行日志
    private final Map<String, Object> workingMemory;        // 工作内存（跨Step状态共享）
    
    // 追踪字段
    private TokenUsage cumulativeUsage;                     // 累计Token使用量
    
    // 配置字段
    private InferencePolicy latestInferencePolicy;
    private ResponseMode latestResponseMode;
    
    // 构造函数
    public Session(String sessionId) {
        this(sessionId, 50, 32000);
    }
    
    public Session(String sessionId, int maxMessages, int maxChars) {
        this.version = 1;
        this.sessionId = sessionId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.lastAccessedAt = this.createdAt;
        this.messages = new ArrayList<>();
        this.executionLog = new ArrayList<>();
        this.workingMemory = new HashMap<>();
        this.cumulativeUsage = TokenUsage.zero();
    }
    
    // 核心方法
    public void addMessage(ConversationMessage message);
    public void addSystemMessage(String content);
    public void addUserMessage(String content);
    public void addAssistantMessage(String content);
    public String recordToolCall(String toolName, String input);
    public void recordToolResult(String toolUseId, String toolName, String output, boolean isError);
    
    // 工作内存方法
    public void putMemory(String key, Object value);
    public Optional<Object> getMemory(String key);
    public <T> Optional<T> getMemory(String key, Class<T> type);
    public void removeMemory(String key);
    public Map<String, Object> getAllMemory();
    
    // 状态方法
    public void touch();                                    // 更新 lastAccessedAt
    public boolean isEmpty();
    public void clear();
    public int estimateTokens();
}
```

### 2.2 ConversationMessage

统一的消息结构，支持 `ContentBlock` 和 `TokenUsage`。

```java
package com.openmanus.saa.model.session;

import java.time.Instant;
import java.util.List;

public class ConversationMessage {
    private final MessageRole role;                         // SYSTEM, USER, ASSISTANT, TOOL
    private final List<ContentBlock> blocks;                // 内容块列表
    private final TokenUsage usage;                         // 该轮对话的Token使用量
    private final Instant timestamp;                        // 时间戳
    
    // 工厂方法
    public static ConversationMessage userText(String text);
    public static ConversationMessage assistant(List<ContentBlock> blocks);
    public static ConversationMessage assistantWithUsage(List<ContentBlock> blocks, TokenUsage usage);
    public static ConversationMessage toolResult(String toolUseId, String toolName, String output, boolean isError);
    public static ConversationMessage system(String content);
}
```

### 2.3 ContentBlock

密封接口，使用 sealed interface 实现三种内容块类型。

```java
package com.openmanus.saa.model.session;

/**
 * 内容块接口（sealed interface）
 */
public sealed interface ContentBlock permits TextBlock, ToolUseBlock, ToolResultBlock {
    String toSummary();     // 用于压缩时生成摘要
    BlockType getType();    // 获取块类型
}

/**
 * 文本块
 */
public record TextBlock(String text) implements ContentBlock {
    @Override
    public String toSummary() {
        return truncate(text, 160);
    }
    
    @Override
    public BlockType getType() {
        return BlockType.TEXT;
    }
}

/**
 * 工具调用块
 */
public record ToolUseBlock(String id, String name, String input) implements ContentBlock {
    @Override
    public String toSummary() {
        return "tool_use " + name + "(" + truncate(input, 100) + ")";
    }
    
    @Override
    public BlockType getType() {
        return BlockType.TOOL_USE;
    }
}

/**
 * 工具结果块
 */
public record ToolResultBlock(
    String toolUseId,
    String toolName,
    String output,
    boolean isError
) implements ContentBlock {
    @Override
    public String toSummary() {
        return (isError ? "error " : "") + "tool_result " + toolName + ": " + truncate(output, 100);
    }
    
    @Override
    public BlockType getType() {
        return BlockType.TOOL_RESULT;
    }
}
```

### 2.4 MessageRole

```java
package com.openmanus.saa.model.session;

public enum MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}
```

### 2.5 TokenUsage

完整 Token 使用量追踪，完整指标。

```java
package com.openmanus.saa.model.session;

public record TokenUsage(
    int inputTokens,
    int outputTokens,
    int cacheCreationInputTokens,
    int cacheReadInputTokens
) {
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

---

## 三、会话压缩设计

### 3.1 压缩配置

```java
package com.openmanus.saa.model.session;

public record CompactionConfig(
    int preserveRecentMessages,     // 保留最近N条消息（默认4）
    int maxEstimatedTokens          // 触发压缩的最大Token数（默认10000）
) {
    public static CompactionConfig defaultConfig() {
        return new CompactionConfig(4, 10000);
    }
}
```

### 3.2 压缩结果

```java
package com.openmanus.saa.model.session;

public record CompactionResult(
    String summary,                     // 原始摘要
    String formattedSummary,            // 格式化后的摘要
    Session compactedSession,           // 压缩后的会话
    int removedMessageCount             // 移除的消息数量
) {}
```

### 3.3 压缩流程

```
shouldCompact(session, config)?
    ↓ Yes
1. 提取现有摘要（如果有）
2. 计算需要压缩的消息范围
3. 生成摘要（summarizeMessages）：
   - 统计消息数量（user/assistant/tool）
   - 提取使用的工具列表
   - 提取最近用户请求
   - 推断待办任务
   - 提取关键文件引用
   - 生成时间线摘要
4. 合并新旧摘要（mergeCompactSummaries）
5. 生成 continuation message
6. 构建压缩后的 Session
```

### 3.4 摘要结构

压缩生成的摘要使用 `<summary>` 标签包裹：

```xml
<summary>
Conversation summary:
- Scope: {count} earlier messages compacted (user={}, assistant={}, tool={}).
- Tools mentioned: {tool_names}.
- Recent user requests:
  - {request1}
  - {request2}
- Pending work:
  - {pending_item}
- Key files referenced: {file1}, {file2}.
- Current work: {current_work}
- Key timeline:
  - user: {content_summary}
  - assistant: {content_summary}
  - tool: {content_summary}
</summary>
```

### 3.5 Continuation Message

```java
public String getCompactContinuationMessage(
    String summary,
    boolean suppressFollowUpQuestions,
    boolean recentMessagesPreserved
) {
    StringBuilder sb = new StringBuilder();
    sb.append("This session is being continued from a previous conversation ")
      .append("that ran out of context. The summary below covers the earlier ")
      .append("portion of the conversation.\n\n");
    sb.append(formatCompactSummary(summary));
    
    if (recentMessagesPreserved) {
        sb.append("\n\nRecent messages are preserved verbatim.");
    }
    
    if (suppressFollowUpQuestions) {
        sb.append("\nContinue the conversation from where it left off ")
          .append("without asking the user any further questions. Resume directly ")
          .append("— do not acknowledge the summary, do not recap what was happening.");
    }
    
    return sb.toString();
}
```

### 3.6 摘要提取函数

#### 3.6.1 推断待办任务

```java
private List<String> inferPendingWork(List<ConversationMessage> messages) {
    List<String> keywords = List.of("todo", "next", "pending", "follow up", "remaining",
                                     "待办", "下一步", "未完成", "后续");
    return messages.stream()
        .collect(Collectors.collectingAndThen(
            Collectors.toList(),
            list -> {
                Collections.reverse(list);
                return list.stream();
            }
        ))
        .map(this::firstTextBlock)
        .filter(text -> keywords.stream().anyMatch(text.toLowerCase()::contains))
        .limit(3)
        .map(text -> truncate(text, 160))
        .collect(Collectors.toList());
}
```

#### 3.6.2 提取关键文件

```java
private List<String> collectKeyFiles(List<ConversationMessage> messages) {
    List<String> interestingExtensions = List.of(
        "rs", "ts", "tsx", "js", "json", "md",
        "java", "xml", "yml", "yaml", "properties", "sql"
    );
    
    return messages.stream()
        .flatMap(m -> m.getBlocks().stream())
        .map(block -> switch (block) {
            case TextBlock t -> t.text();
            case ToolUseBlock tu -> tu.input();
            case ToolResultBlock tr -> tr.output();
        })
        .flatMap(this::extractFileCandidates)
        .distinct()
        .limit(8)
        .collect(Collectors.toList());
}

private Stream<String> extractFileCandidates(String content) {
    return Arrays.stream(content.split("\\s+"))
        .map(token -> token.replaceAll("[,\\.:;()\"'`]", ""))
        .filter(candidate -> candidate.contains("/"))
        .filter(candidate -> {
            int dotIndex = candidate.lastIndexOf('.');
            if (dotIndex < 0) return false;
            String ext = candidate.substring(dotIndex + 1).toLowerCase();
            return interestingExtensions.contains(ext);
        });
}
```

---

## 四、服务层设计

### 4.1 SessionManager

```java
package com.openmanus.saa.service.session;

import org.springframework.scheduling.annotation.Scheduled;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一的会话管理服务
 */
@Service
public class SessionManager {

    private final SessionStorage storage;
    private final SessionCompactor compactor;
    private final SessionConfig config;
    
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    
    // ================== 核心方法 ==================
    
    /**
     * 创建新会话
     */
    public Session createSession(String sessionId) {
        Session session = new Session(sessionId);
        sessions.put(sessionId, session);
        storage.save(sessionId, session);
        return session;
    }
    
    /**
     * 获取会话（不存在则创建）
     */
    public Session getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> {
            Optional<Session> loaded = storage.load(id);
            return loaded.orElseGet(() -> new Session(id));
        });
    }
    
    /**
     * 添加消息并自动压缩
     */
    public void addMessage(String sessionId, ConversationMessage message) {
        Session session = getOrCreateSession(sessionId);
        session.addMessage(message);
        
        // 自动压缩检查
        if (compactor.shouldCompact(session, config.getCompactionConfig())) {
            CompactionResult result = compactor.compactSession(session, config.getCompactionConfig());
            sessions.put(sessionId, result.compactedSession());
        }
        
        storage.save(sessionId, session);
    }
    
    /**
     * 手动触发压缩
     */
    public CompactionResult compactSession(String sessionId) {
        Session session = getOrCreateSession(sessionId);
        CompactionResult result = compactor.compactSession(session, config.getCompactionConfig());
        sessions.put(sessionId, result.compactedSession());
        storage.save(sessionId, result.compactedSession());
        return result;
    }
    
    /**
     * 获取累计Token使用量
     */
    public TokenUsage getCumulativeUsage(String sessionId) {
        return sessions.getOrDefault(sessionId, new Session(sessionId))
            .getCumulativeUsage();
    }
    
    // ================== TTL 清理 ==================
    
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minus(config.getSessionTTL());
        sessions.entrySet().removeIf(entry -> 
            entry.getValue().getLastAccessedAt().isBefore(cutoff)
        );
    }
}
```

### 4.2 SessionCompactor

```java
package com.openmanus.saa.service.session;

import org.springframework.stereotype.Component;

/**
 * 会话压缩器，完整复刻 Claw Code 的压缩逻辑
 */
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

    /**
     * 估算会话Token数
     */
    public int estimateSessionTokens(Session session) {
        return session.getMessages().stream()
            .mapToInt(this::estimateMessageTokens)
            .sum();
    }

    /**
     * 判断是否需要压缩
     */
    public boolean shouldCompact(Session session, CompactionConfig config) {
        int start = compactedSummaryPrefixLen(session);
        List<ConversationMessage> compactable = session.getMessages().subList(start, session.getMessages().size());
        
        if (compactable.size() <= config.preserveRecentMessages()) {
            return false;
        }
        
        int compactableTokens = compactable.stream()
            .mapToInt(this::estimateMessageTokens)
            .sum();
        
        return compactableTokens >= config.maxEstimatedTokens();
    }

    /**
     * 执行压缩
     */
    public CompactionResult compactSession(Session session, CompactionConfig config) {
        if (!shouldCompact(session, config)) {
            return new CompactionResult("", "", session, 0);
        }
        
        // 提取现有摘要
        Optional<String> existingSummary = session.getMessages().stream()
            .findFirst()
            .flatMap(this::extractExistingCompactedSummary);
        
        int compactedPrefixLen = existingSummary.map(s -> 1).orElse(0);
        int keepFrom = session.getMessages().size() - config.preserveRecentMessages();
        
        List<ConversationMessage> removed = session.getMessages().subList(compactedPrefixLen, keepFrom);
        List<ConversationMessage> preserved = new ArrayList<>(session.getMessages().subList(keepFrom, session.getMessages().size()));
        
        // 生成摘要
        String newSummary = summarizeMessages(removed);
        String summary = mergeCompactSummaries(existingSummary.orElse(null), newSummary);
        String formattedSummary = formatCompactSummary(summary);
        String continuation = getCompactContinuationMessage(summary, true, !preserved.isEmpty());
        
        // 构建压缩后的会话
        List<ConversationMessage> compactedMessages = new ArrayList<>();
        compactedMessages.add(ConversationMessage.system(continuation));
        compactedMessages.addAll(preserved);
        
        Session compactedSession = session.withMessages(compactedMessages);
        
        return new CompactionResult(summary, formattedSummary, compactedSession, removed.size());
    }

    // ... 其他私有方法（summarizeMessages, collectKeyFiles, inferPendingWork 等）
}
```

### 4.3 SessionStorage 接口

```java
package com.openmanus.saa.service.session.storage;

/**
 * 会话存储接口（策略模式）
 */
public interface SessionStorage {
    void save(String sessionId, Session session);
    Optional<Session> load(String sessionId);
    void delete(String sessionId);
    List<String> listAllSessionIds();
}
```

实现类：
- `MemorySessionStorage` - 内存存储（开发/测试）
- `MybatisSessionStorage` - MySQL 持久化（生产）
- `JsonFileSessionStorage` - JSON 文件存储（可选）

---

## 五、Spring AI 集成

### 5.1 SessionToSpringAIConverter

```java
package com.openmanus.saa.service.session;

import org.springframework.ai.chat.messages.*;

/**
 * 将 Session 的消息转换为 Spring AI 的 Message 格式
 */
@Component
public class SessionToSpringAIConverter {

    /**
     * 转换整个 Session 的消息历史
     */
    public List<Message> convert(Session session) {
        return session.getMessages().stream()
            .map(this::convertMessage)
            .collect(Collectors.toList());
    }
    
    /**
     * 转换单条消息
     */
    private Message convertMessage(ConversationMessage msg) {
        return switch (msg.getRole()) {
            case SYSTEM -> new SystemMessage(convertBlocks(msg.getBlocks()));
            case USER -> new UserMessage(convertBlocks(msg.getBlocks()));
            case ASSISTANT -> new AssistantMessage(convertBlocks(msg.getBlocks()));
            case TOOL -> convertToolMessage(msg);
        };
    }
    
    /**
     * 转换 ContentBlock 列表为文本
     */
    private String convertBlocks(List<ContentBlock> blocks) {
        return blocks.stream()
            .map(block -> switch (block) {
                case TextBlock t -> t.text();
                case ToolUseBlock tu -> formatToolUse(tu);
                case ToolResultBlock tr -> formatToolResult(tr);
            })
            .collect(Collectors.joining("\n"));
    }
    
    /**
     * Tool 消息转换为 UserMessage（Spring AI 版本兼容处理）
     */
    private Message convertToolMessage(ConversationMessage msg) {
        String content = msg.getBlocks().stream()
            .map(b -> formatToolResult((ToolResultBlock) b))
            .collect(Collectors.joining("\n"));
        return new UserMessage(content);
    }
    
    private String formatToolUse(ToolUseBlock tu) {
        return String.format("[Tool Use: %s, Input: %s]", tu.name(), tu.input());
    }
    
    private String formatToolResult(ToolResultBlock tr) {
        return String.format("[Tool Result: %s, Output: %s%s]",
            tr.toolName(), tr.isError() ? "ERROR: " : "", tr.output());
    }
}
```

### 5.2 TokenUsageExtractor

```java
package com.openmanus.saa.service.session;

import org.springframework.ai.chat.model.ChatResponse;

/**
 * 从 ChatResponse 提取 TokenUsage
 */
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
            0,  // cache_creation_input_tokens（Spring AI 暂不支持）
            0   // cache_read_input_tokens（Spring AI 暂不支持）
        );
    }
}
```

---

## 六、持久化设计

### 6.1 SessionSerializer

```java
package com.openmanus.saa.service.session;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Session JSON 序列化/反序列化
 */
@Component
public class SessionSerializer {

    private final Gson gson = new Gson();
    
    public String toJson(Session session) {
        JsonObject obj = new JsonObject();
        obj.addProperty("version", session.getVersion());
        obj.addProperty("sessionId", session.getSessionId());
        obj.addProperty("createdAt", session.getCreatedAt().toString());
        obj.addProperty("updatedAt", session.getUpdatedAt().toString());
        obj.addProperty("lastAccessedAt", session.getLastAccessedAt().toString());
        obj.add("messages", serializeMessages(session.getMessages()));
        obj.add("workingMemory", gson.toJsonTree(session.getAllMemory()));
        obj.add("executionLog", gson.toJsonTree(session.getExecutionLog()));
        
        if (session.getCumulativeUsage() != null) {
            obj.add("cumulativeUsage", serializeTokenUsage(session.getCumulativeUsage()));
        }
        
        return obj.toString();
    }
    
    public Session fromJson(String json);
}
```

### 6.2 数据库表设计（MySQL）

```sql
CREATE TABLE session (
    session_id VARCHAR(64) PRIMARY KEY,
    version INT DEFAULT 1,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    last_accessed_at TIMESTAMP NOT NULL,
    content JSON NOT NULL,           -- 序列化的 Session 数据
    cumulative_input_tokens INT DEFAULT 0,
    cumulative_output_tokens INT DEFAULT 0,
    cumulative_cache_creation_tokens INT DEFAULT 0,
    cumulative_cache_read_tokens INT DEFAULT 0
);

CREATE INDEX idx_session_last_accessed ON session(last_accessed_at);
```

---

## 七、迁移方案

### 7.1 需要替换的类

| 原类 | 新类 | 说明 |
|------|------|------|
| `SessionState` | `Session` | 统一模型 |
| `ConversationSession` | `Session` | 统一模型 |
| `SessionMemoryService` | `SessionManager` | 统一服务 |
| `SessionMessages` | `Session.messages + ContentBlock` | 数据结构替代 |
| `ConversationMessage(role, content)` | `ConversationMessage(role, blocks, usage)` | 结构升级 |

### 7.2 需要适配的类

| 类 | 适配内容 |
|------|----------|
| `WorkflowService` | 使用 `SessionManager` |
| `GeneralAgentExecutor` | 使用新的 Session API |
| `SessionAwareToolCallback` | 使用 `ContentBlock` 记录 Tool 调用 |
| `AgentController` | 返回新的 Session 数据格式 |
| `SupervisorAgentService` | 使用统一 Session |

### 7.3 兼容性处理

迁移过渡期，提供旧版到新版的转换器：

```java
public class MessageCompatibilityConverter {
    public ConversationMessage convert(String role, String content, Instant timestamp) {
        return new ConversationMessage(
            MessageRole.valueOf(role.toUpperCase()),
            List.of(new TextBlock(content)),
            null,
            timestamp
        );
    }
}
```

---

## 八、包结构

```
com.openmanus.saa.model.session/
    ├── Session.java                  # 统一会话模型
    ├── ConversationMessage.java      # 消息结构（含 blocks + usage）
    ├── ContentBlock.java             # sealed interface
    ├── TextBlock.java                # record 实现
    ├── ToolUseBlock.java             # record 实现
    ├── ToolResultBlock.java          # record 实现
    ├── BlockType.java                # 块类型枚举
    ├── MessageRole.java              # 角色枚举
    ├── TokenUsage.java               # Token使用量
    ├── CompactionConfig.java         # 压缩配置
    └── CompactionResult.java         # 压缩结果

com.openmanus.saa.service.session/
    ├── SessionManager.java           # 统一会话管理服务
    ├── SessionCompactor.java         # 压缩器（完整复刻 Claw）
    ├── SessionSerializer.java        # JSON 序列化
    ├── SessionToSpringAIConverter.java # Spring AI 转换器
    ├── TokenUsageExtractor.java      # Token 提取器
    └── storage/
        ├── SessionStorage.java       # 存储接口
        ├── MemorySessionStorage.java # 内存存储实现
        └── MybatisSessionStorage.java # MySQL 存储实现
```

---

## 九、验收标准

1. **Session 统一** - 项目中不再存在 `SessionState` 和 `ConversationSession` 两个类
2. **ContentBlock 支持** - 所有消息使用 `ContentBlock` 抽象，支持 `TextBlock`/`ToolUseBlock`/`ToolResultBlock`
3. **Token 追踪** - 每条 `ConversationMessage` 可携带 `TokenUsage`，`Session` 累计统计
4. **会话压缩** - 实现完整的压缩逻辑，包括 continuation message、关键上下文保留、多次压缩合并
5. **Spring AI 集成** - `SessionToSpringAIConverter` 正确转换消息格式
6. **持久化** - `Session` 可序列化为 JSON，支持 MySQL 存储
7. **向后兼容** - 提供迁移转换器，现有功能不受影响

---

## 十、待办事项

- [ ] 创建核心数据结构类（Session, ConversationMessage, ContentBlock 等）
- [ ] 实现 SessionCompactor（完整复刻 Claw 压缩逻辑）
- [ ] 实现 SessionManager
- [ ] 实现 SessionStorage 接口（Memory + MyBatis）
- [ ] 实现 SessionSerializer
- [ ] 实现 SessionToSpringAIConverter
- [ ] 实现 TokenUsageExtractor
- [ ] 迁移现有代码到新 Session 模型
- [ ] 添加单元测试
- [ ] 集成测试验证
