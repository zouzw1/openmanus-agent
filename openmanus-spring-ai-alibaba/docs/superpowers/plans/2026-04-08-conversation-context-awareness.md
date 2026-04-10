# 对话上下文感知系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现对话上下文感知，让 Agent 能够理解用户意图的连续性，包括：补充信息、进一步要求、新内容处理，以及用户偏好的继承。

**Architecture:** 创建 ConversationContext 统一上下文对象，在 Session.workingMemory 中存储用户偏好和工作流状态，在 Planning/执行流程中注入对话历史。

**Tech Stack:** Java 17+, Spring AI, spring-ai-alibaba-graph

---

## Task 1: 创建 WorkflowState 数据结构

**Files:**
- Create: `src/main/java/com/openmanus/saa/model/context/WorkflowState.java`
- Create: `src/test/java/com/openmanus/saa/model/context/WorkflowStateTest.java`

- [ ] **Step 1: 创建 WorkflowState record**

```java
package com.openmanus.saa.model.context;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.WorkflowStep;
import java.time.Instant;
import java.util.List;

/**
 * 工作流状态，用于会话级持久化和恢复。
 */
public record WorkflowState(
    WorkflowStatus status,
    String objective,
    String planId,
    int currentStepIndex,
    List<WorkflowStep> steps,
    HumanFeedbackRequest pendingFeedback,
    String lastDeliverable,
    Instant lastActiveAt
) {
    public enum WorkflowStatus {
        NONE,           // 无工作流
        IN_PROGRESS,    // 执行中
        PAUSED,         // 暂停等待反馈
        COMPLETED,      // 已完成
        FAILED          // 失败
    }

    public static WorkflowState none() {
        return new WorkflowState(WorkflowStatus.NONE, null, null, 0, null, null, null, null);
    }

    public static WorkflowState inProgress(String objective, String planId, List<WorkflowStep> steps) {
        return new WorkflowState(WorkflowStatus.IN_PROGRESS, objective, planId, 0, steps, null, null, Instant.now());
    }

    public static WorkflowState paused(String objective, String planId, int stepIndex,
            List<WorkflowStep> steps, HumanFeedbackRequest pendingFeedback) {
        return new WorkflowState(WorkflowStatus.PAUSED, objective, planId, stepIndex, steps, pendingFeedback, null, Instant.now());
    }

    public static WorkflowState completed(String objective, String planId, String lastDeliverable) {
        return new WorkflowState(WorkflowStatus.COMPLETED, objective, planId, 0, null, null, lastDeliverable, Instant.now());
    }

    public static WorkflowState failed(String objective, String planId, int stepIndex, List<WorkflowStep> steps) {
        return new WorkflowState(WorkflowStatus.FAILED, objective, planId, stepIndex, steps, null, null, Instant.now());
    }

    public boolean isPaused() {
        return status == WorkflowStatus.PAUSED;
    }

    public boolean isCompleted() {
        return status == WorkflowStatus.COMPLETED;
    }

    public boolean hasActiveWorkflow() {
        return status != WorkflowStatus.NONE;
    }
}
```

- [ ] **Step 2: 编写单元测试**

```java
package com.openmanus.saa.model.context;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowStateTest {

    @Test
    void none_shouldReturnEmptyState() {
        WorkflowState state = WorkflowState.none();
        assertEquals(WorkflowState.WorkflowStatus.NONE, state.status());
        assertFalse(state.isPaused());
        assertFalse(state.hasActiveWorkflow());
    }

    @Test
    void paused_shouldReturnPausedState() {
        WorkflowState state = WorkflowState.paused("test objective", "plan-1", 2, null, null);
        assertTrue(state.isPaused());
        assertTrue(state.hasActiveWorkflow());
        assertEquals(2, state.currentStepIndex());
    }

    @Test
    void completed_shouldReturnCompletedState() {
        WorkflowState state = WorkflowState.completed("test objective", "plan-1", "deliverable content");
        assertTrue(state.isCompleted());
        assertEquals("deliverable content", state.lastDeliverable());
    }
}
```

- [ ] **Step 3: 运行测试验证**

Run: `mvn test -pl openmanus-spring-ai-alibaba -Dtest=WorkflowStateTest -q`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/openmanus/saa/model/context/WorkflowState.java src/test/java/com/openmanus/saa/model/context/WorkflowStateTest.java
git commit -m "feat: add WorkflowState for session-level workflow persistence"
```

---

## Task 2: 创建 ConversationContext 数据结构

**Files:**
- Create: `src/main/java/com/openmanus/saa/model/context/ConversationContext.java`
- Create: `src/test/java/com/openmanus/saa/model/context/ConversationContextTest.java`

- [ ] **Step 1: 创建 ConversationContext record**

```java
package com.openmanus.saa.model.context;

import com.openmanus.saa.model.session.ConversationMessage;
import com.openmanus.saa.model.session.Session;
import java.util.List;
import java.util.Map;

/**
 * 统一对话上下文对象，在入口处一次性构建，贯穿整个执行流程。
 */
public record ConversationContext(
    // 会话标识
    String sessionId,
    Session session,

    // 对话历史
    String conversationHistory,
    List<ConversationMessage> messages,

    // 用户偏好（从 workingMemory 提取）
    Map<String, Object> userPreferences,

    // 工作流状态
    WorkflowState workflowState,

    // 当前请求
    String currentPrompt
) {
    private static final String USER_PREFERENCES_KEY = "userPreferences";
    private static final String WORKFLOW_STATE_KEY = "workflowState";

    /**
     * 从 Session 构建 ConversationContext。
     */
    public static ConversationContext from(Session session, String currentPrompt, String conversationHistory) {
        Map<String, Object> prefs = extractPreferences(session);
        WorkflowState state = extractWorkflowState(session);
        return new ConversationContext(
            session.sessionId(),
            session,
            conversationHistory,
            session.messages(),
            prefs,
            state,
            currentPrompt
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractPreferences(Session session) {
        return session.getMemory(USER_PREFERENCES_KEY, Map.class).orElse(Map.of());
    }

    private static WorkflowState extractWorkflowState(Session session) {
        return session.getMemory(WORKFLOW_STATE_KEY, WorkflowState.class).orElse(WorkflowState.none());
    }

    /**
     * 是否有暂停的工作流需要恢复。
     */
    public boolean hasPausedWorkflow() {
        return workflowState != null && workflowState.isPaused();
    }

    /**
     * 是否有用户偏好。
     */
    public boolean hasUserPreferences() {
        return userPreferences != null && !userPreferences.isEmpty();
    }

    /**
     * 获取偏好值的便捷方法。
     */
    @SuppressWarnings("unchecked")
    public <T> T getPreference(String key, Class<T> type, T defaultValue) {
        if (userPreferences == null) {
            return defaultValue;
        }
        Object value = userPreferences.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return defaultValue;
    }
}
```

- [ ] **Step 2: 编写单元测试**

```java
package com.openmanus.saa.model.context;

import com.openmanus.saa.model.session.Session;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConversationContextTest {

    @Test
    void from_shouldExtractPreferencesAndWorkflowState() {
        Session session = new Session("test-session");
        session = session.putMemory("userPreferences", Map.of("diet", "喜欢吃辣"));
        session = session.putMemory("workflowState", WorkflowState.completed("test", "plan-1", "result"));

        ConversationContext ctx = ConversationContext.from(session, "new prompt", "history");

        assertEquals("test-session", ctx.sessionId());
        assertEquals("new prompt", ctx.currentPrompt());
        assertTrue(ctx.hasUserPreferences());
        assertTrue(ctx.workflowState().isCompleted());
    }

    @Test
    void from_emptySession_shouldReturnDefaults() {
        Session session = new Session("test-session");
        ConversationContext ctx = ConversationContext.from(session, "prompt", null);

        assertFalse(ctx.hasUserPreferences());
        assertFalse(ctx.hasPausedWorkflow());
        assertEquals(WorkflowState.WorkflowStatus.NONE, ctx.workflowState().status());
    }

    @Test
    void getPreference_shouldReturnValueWithType() {
        Session session = new Session("test-session");
        session = session.putMemory("userPreferences", Map.of("budget", 500));
        ConversationContext ctx = ConversationContext.from(session, "prompt", null);

        Integer budget = ctx.getPreference("budget", Integer.class, 0);
        assertEquals(500, budget);
    }
}
```

- [ ] **Step 3: 运行测试验证**

Run: `mvn test -pl openmanus-spring-ai-alibaba -Dtest=ConversationContextTest -q`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/openmanus/saa/model/context/ConversationContext.java src/test/java/com/openmanus/saa/model/context/ConversationContextTest.java
git commit -m "feat: add ConversationContext for unified context management"
```

---

## Task 3: 增强 SessionMemoryService

**Files:**
- Modify: `src/main/java/com/openmanus/saa/service/session/SessionMemoryService.java`
- Create: `src/test/java/com/openmanus/saa/service/session/SessionMemoryServiceContextTest.java`

- [ ] **Step 1: 添加偏好相关方法**

在 `SessionMemoryService` 中添加：

```java
private static final String USER_PREFERENCES_KEY = "userPreferences";
private static final String WORKFLOW_STATE_KEY = "workflowState";

// ========== 用户偏好 ==========

public Session saveUserPreference(String sessionId, String key, Object value) {
    Session session = storage.findById(sessionId)
        .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

    @SuppressWarnings("unchecked")
    Map<String, Object> prefs = session.getMemory(USER_PREFERENCES_KEY, Map.class)
        .orElse(new java.util.HashMap<>());

    Map<String, Object> newPrefs = new java.util.HashMap<>(prefs);
    newPrefs.put(key, value);

    Session updated = session.putMemory(USER_PREFERENCES_KEY, java.util.Map.copyOf(newPrefs));
    return storage.save(updated);
}

public Optional<Object> getUserPreference(String sessionId, String key) {
    return storage.findById(sessionId)
        .flatMap(session -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> prefs = session.getMemory(USER_PREFERENCES_KEY, Map.class).orElse(null);
            if (prefs == null) return Optional.empty();
            return Optional.ofNullable(prefs.get(key));
        });
}

public Map<String, Object> getUserPreferences(String sessionId) {
    return storage.findById(sessionId)
        .flatMap(session -> session.getMemory(USER_PREFERENCES_KEY, Map.class))
        .orElse(Map.of());
}

// ========== 工作流状态 ==========

public void saveWorkflowState(String sessionId, WorkflowState state) {
    Session session = storage.findById(sessionId)
        .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    Session updated = session.putMemory(WORKFLOW_STATE_KEY, state);
    storage.save(updated);
    log.debug("Saved workflow state for session {}: {}", sessionId, state.status());
}

public Optional<WorkflowState> getWorkflowState(String sessionId) {
    return storage.findById(sessionId)
        .flatMap(session -> session.getMemory(WORKFLOW_STATE_KEY, WorkflowState.class));
}

public void clearWorkflowState(String sessionId) {
    Session session = storage.findById(sessionId).orElse(null);
    if (session != null) {
        Session updated = session.removeMemory(WORKFLOW_STATE_KEY);
        storage.save(updated);
        log.debug("Cleared workflow state for session {}", sessionId);
    }
}
```

- [ ] **Step 2: 实现 pendingFeedback 持久化**

修改现有的 `savePendingFeedback` 和 `getPendingFeedback` 方法：

```java
public void savePendingFeedback(String sessionId, HumanFeedbackRequest request) {
    Session session = storage.findById(sessionId)
        .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

    // 同时保存到 workingMemory 和 workflowState
    WorkflowState state = WorkflowState.paused(
        request.getObjective(),
        request.getPlanId(),
        request.getStepIndex(),
        request.getSteps(),
        request
    );

    Session updated = session.putMemory(WORKFLOW_STATE_KEY, state);
    storage.save(updated);

    log.info("Saved pending feedback for session {}: stepIndex={}, objective='{}'",
        sessionId, request.getStepIndex(), request.getObjective());
}

public Optional<HumanFeedbackRequest> getPendingFeedback(String sessionId) {
    return getWorkflowState(sessionId)
        .filter(WorkflowState::isPaused)
        .map(WorkflowState::pendingFeedback);
}

public void clearPendingFeedback(String sessionId) {
    clearWorkflowState(sessionId);
    log.info("Cleared pending feedback for session {}", sessionId);
}

public boolean hasPendingFeedback(String sessionId) {
    return getWorkflowState(sessionId)
        .map(WorkflowState::isPaused)
        .orElse(false);
}
```

- [ ] **Step 3: 编写测试**

```java
package com.openmanus.saa.service.session;

import com.openmanus.saa.model.context.WorkflowState;
import com.openmanus.saa.model.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SessionMemoryServiceContextTest {

    private SessionMemoryService service;
    private Session session;

    @BeforeEach
    void setUp() {
        // Use in-memory storage for testing
        var storage = new MemorySessionStorage();
        var compactor = new SessionCompactor(null);
        var config = new com.openmanus.saa.config.SessionConfig();
        service = new SessionMemoryService(storage, compactor, config);
        session = service.getOrCreate("test-session");
    }

    @Test
    void saveUserPreference_shouldPersist() {
        service.saveUserPreference("test-session", "diet", "喜欢吃辣");

        var pref = service.getUserPreference("test-session", "diet");
        assertTrue(pref.isPresent());
        assertEquals("喜欢吃辣", pref.get());
    }

    @Test
    void saveWorkflowState_shouldPersist() {
        WorkflowState state = WorkflowState.completed("test objective", "plan-1", "result");
        service.saveWorkflowState("test-session", state);

        var retrieved = service.getWorkflowState("test-session");
        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get().isCompleted());
    }

    @Test
    void getPendingFeedback_whenPaused_shouldReturn() {
        var feedback = new com.openmanus.saa.model.HumanFeedbackRequest(
            "test-session", "objective", "plan-1", 2, null, null, "error", "retry"
        );
        service.savePendingFeedback("test-session", feedback);

        var pending = service.getPendingFeedback("test-session");
        assertTrue(pending.isPresent());
        assertEquals(2, pending.get().getStepIndex());
    }
}
```

- [ ] **Step 4: 运行测试验证**

Run: `mvn test -pl openmanus-spring-ai-alibaba -Dtest=SessionMemoryServiceContextTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openmanus/saa/service/session/SessionMemoryService.java src/test/java/com/openmanus/saa/service/session/SessionMemoryServiceContextTest.java
git commit -m "feat: add preference and workflow state persistence to SessionMemoryService"
```

---

## Task 4: 创建 ConversationContextFactory

**Files:**
- Create: `src/main/java/com/openmanus/saa/service/context/ConversationContextFactory.java`
- Create: `src/test/java/com/openmanus/saa/service/context/ConversationContextFactoryTest.java`

- [ ] **Step 1: 创建工厂类**

```java
package com.openmanus.saa.service.context;

import com.openmanus.saa.model.context.ConversationContext;
import com.openmanus.saa.model.session.Session;
import com.openmanus.saa.service.session.SessionMemoryService;
import org.springframework.stereotype.Component;

@Component
public class ConversationContextFactory {

    private static final int DEFAULT_HISTORY_LIMIT = 10;

    private final SessionMemoryService sessionMemoryService;

    public ConversationContextFactory(SessionMemoryService sessionMemoryService) {
        this.sessionMemoryService = sessionMemoryService;
    }

    /**
     * 构建 ConversationContext。
     */
    public ConversationContext create(String sessionId, String currentPrompt) {
        Session session = sessionMemoryService.getOrCreate(sessionId);
        String history = sessionMemoryService.summarizeHistory(session, DEFAULT_HISTORY_LIMIT);
        return ConversationContext.from(session, currentPrompt, history);
    }

    /**
     * 构建带自定义历史长度的 ConversationContext。
     */
    public ConversationContext create(String sessionId, String currentPrompt, int historyLimit) {
        Session session = sessionMemoryService.getOrCreate(sessionId);
        String history = sessionMemoryService.summarizeHistory(session, historyLimit);
        return ConversationContext.from(session, currentPrompt, history);
    }
}
```

- [ ] **Step 2: 编写测试**

```java
package com.openmanus.saa.service.context;

import com.openmanus.saa.model.session.Session;
import com.openmanus.saa.service.session.SessionMemoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationContextFactoryTest {

    @Mock
    private SessionMemoryService sessionMemoryService;

    @Test
    void create_shouldBuildContextWithHistory() {
        Session session = new Session("test-session");
        when(sessionMemoryService.getOrCreate("test-session")).thenReturn(session);
        when(sessionMemoryService.summarizeHistory(any(), anyInt())).thenReturn("previous conversation");

        ConversationContextFactory factory = new ConversationContextFactory(sessionMemoryService);
        ConversationContext ctx = factory.create("test-session", "what's the weather?");

        assertEquals("test-session", ctx.sessionId());
        assertEquals("what's the weather?", ctx.currentPrompt());
        assertEquals("previous conversation", ctx.conversationHistory());
    }
}
```

- [ ] **Step 3: 运行测试验证**

Run: `mvn test -pl openmanus-spring-ai-alibaba -Dtest=ConversationContextFactoryTest -q`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/openmanus/saa/service/context/ConversationContextFactory.java src/test/java/com/openmanus/saa/service/context/ConversationContextFactoryTest.java
git commit -m "feat: add ConversationContextFactory for context building"
```

---

## Task 5: 在 PlanningService 中注入对话历史

**Files:**
- Modify: `src/main/java/com/openmanus/saa/service/PlanningService.java`

- [ ] **Step 1: 添加重载方法支持 ConversationContext**

在 `PlanningService` 中添加：

```java
/**
 * 创建工作流计划（带对话上下文）。
 */
public List<WorkflowStep> createWorkflowPlan(
    String objective,
    List<AgentCapabilitySnapshot> agentSnapshots,
    IntentResolution intentResolution,
    ConversationContext context
) {
    validateObjectiveCapabilitySupportOrThrow(objective, agentSnapshots);
    DraftPlan draftPlan = createWorkflowDraft(objective, agentSnapshots, intentResolution, context);
    draftPlan.lintWarnings().forEach(warning -> log.warn("Workflow draft lint: {}", warning));
    return compileWorkflowPlan(objective, draftPlan.steps(), agentSnapshots);
}

private DraftPlan createWorkflowDraft(
    String objective,
    List<AgentCapabilitySnapshot> agentSnapshots,
    IntentResolution intentResolution,
    ConversationContext context
) {
    String toolsSchema = buildPlanningToolGuidance();
    String languageDirective = ResponseLanguageHelper.responseDirective(objective);
    String availableAgents = buildAgentCapabilityPrompt(agentSnapshots);
    Set<String> allowedAgents = agentSnapshots.stream()
        .map(AgentCapabilitySnapshot::agentId)
        .filter(agentId -> agentId != null && !agentId.isBlank())
        .collect(Collectors.toSet());
    String intentContext = buildIntentPlanningContext(intentResolution);
    String conversationContext = buildConversationContextPrompt(context);

    String content = chatClient.prompt()
        .system("""
            You are an expert Planning Agent tasked with solving problems efficiently through structured plans.

            Your job is:
            1. Create a clear, actionable workflow plan with assigned agents.
            2. Break tasks into 2-5 concrete, non-overlapping steps.
            3. For each step, assign exactly one agent from available agents.
            4. Make the first step directly executable.
            5. Avoid duplicate or near-duplicate steps.
            6. Do not include reasoning, summaries, or commentary as steps.

            Available agents:
            %s

            Intent context:
            %s

            %s

            AVAILABLE TOOLS AND THEIR SCHEMAS:
            %s

            IMPORTANT PLANNING RULES:
            1. Each step must be one executable action.
            2. The "agent" field MUST be one of the available agents, never a tool name.
            3. CRITICAL: Before assigning an agent to a step, verify that the agent has the required tools.
            4. Consider the conversation history and user preferences when planning.
            5. If the user mentioned preferences (like dietary restrictions), incorporate them into the plan.

            Return JSON only as an array.
            Each item must follow this schema:
            {
              "agent": "manus",
              "description": "use the appropriate tool to retrieve the required data"
            }

            %s
            """.formatted(availableAgents, intentContext, conversationContext, toolsSchema, languageDirective))
        .user("""
            Task: %s

            Output 2-5 executable steps only.
            Do not include reasoning.
            """.formatted(objective))
        .call()
        .content();

    List<WorkflowStep> parsedSteps = parseWorkflowPlan(content, allowedAgents);
    List<WorkflowStep> sanitizedSteps = sanitizeWorkflowSteps(parsedSteps, allowedAgents);
    return new DraftPlan(sanitizedSteps, lintWorkflowDraft(parsedSteps, sanitizedSteps));
}

/**
 * 构建对话上下文提示。
 */
private String buildConversationContextPrompt(ConversationContext context) {
    if (context == null) {
        return "";
    }

    StringBuilder sb = new StringBuilder();

    // 对话历史
    if (context.conversationHistory() != null && !context.conversationHistory().isBlank()) {
        sb.append("CONVERSATION HISTORY:\n")
          .append(context.conversationHistory())
          .append("\n\n");
    }

    // 用户偏好
    if (context.hasUserPreferences()) {
        sb.append("USER PREFERENCES:\n");
        context.userPreferences().forEach((key, value) ->
            sb.append("- ").append(key).append(": ").append(value).append("\n")
        );
        sb.append("\n");
    }

    return sb.toString();
}
```

- [ ] **Step 2: 保留原方法向后兼容**

原有的 `createWorkflowPlan(objective, agentSnapshots, intentResolution)` 方法保持不变，调用新的带 context 的方法：

```java
public List<WorkflowStep> createWorkflowPlan(
    String objective,
    List<AgentCapabilitySnapshot> agentSnapshots,
    IntentResolution intentResolution
) {
    return createWorkflowPlan(objective, agentSnapshots, intentResolution, null);
}
```

- [ ] **Step 3: 运行编译验证**

Run: `mvn compile -pl openmanus-spring-ai-alibaba -q`
Expected: SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/openmanus/saa/service/PlanningService.java
git commit -m "feat: inject conversation context into PlanningService"
```

---

## Task 6: 创建 PreferenceExtractor 偏好提取器

**Files:**
- Create: `src/main/java/com/openmanus/saa/service/context/PreferenceExtractor.java`
- Create: `src/test/java/com/openmanus/saa/service/context/PreferenceExtractorTest.java`

- [ ] **Step 1: 创建偏好提取器**

```java
package com.openmanus.saa.service.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.session.ConversationMessage;
import com.openmanus.saa.model.session.ContentBlock;
import com.openmanus.saa.model.session.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从对话中提取用户偏好。
 */
@Component
public class PreferenceExtractor {

    private static final Logger log = LoggerFactory.getLogger(PreferenceExtractor.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public PreferenceExtractor(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 从最近的对话中提取用户偏好。
     *
     * @param messages 对话消息列表
     * @param currentObjective 当前目标（可选）
     * @return 提取的偏好 Map
     */
    public Map<String, Object> extractPreferences(List<ConversationMessage> messages, String currentObjective) {
        if (messages == null || messages.isEmpty()) {
            return Map.of();
        }

        // 获取最近几轮用户消息
        List<String> userInputs = messages.stream()
            .filter(m -> m.role() == com.openmanus.saa.model.session.MessageRole.USER)
            .map(m -> extractText(m))
            .filter(t -> t != null && !t.isBlank())
            .toList();

        if (userInputs.isEmpty()) {
            return Map.of();
        }

        try {
            String content = chatClient.prompt()
                .system("""
                    Extract user preferences from the conversation.
                    Look for explicit preferences like:
                    - Dietary restrictions (e.g., "我喜欢吃辣", "I don't eat seafood")
                    - Budget preferences (e.g., "预算500元", "mid-range")
                    - Travel style (e.g., "深度游", "relaxing vacation")
                    - Language preference
                    - Any other preferences that should be remembered

                    Return JSON only in this format:
                    {
                      "preferences": {
                        "diet": ["喜欢吃辣", "不吃海鲜"],
                        "budget": "中等",
                        "travel_style": "深度游"
                      }
                    }

                    If no preferences found, return: {"preferences": {}}
                    Only extract EXPLICITLY stated preferences, do not infer.
                    """)
                .user("""
                    Recent user messages:
                    %s

                    Current objective:
                    %s
                    """.formatted(String.join("\n", userInputs), currentObjective != null ? currentObjective : "N/A"))
                .call()
                .content();

            if (content == null || content.isBlank()) {
                return Map.of();
            }

            String normalized = content.replace("```json", "").replace("```", "").trim();
            JsonNode root = objectMapper.readTree(normalized);
            JsonNode prefsNode = root.path("preferences");

            if (!prefsNode.isObject()) {
                return Map.of();
            }

            Map<String, Object> result = new HashMap<>();
            prefsNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isArray()) {
                    List<String> list = new ArrayList<>();
                    value.forEach(item -> list.add(item.asText()));
                    result.put(entry.getKey(), List.copyOf(list));
                } else {
                    result.put(entry.getKey(), value.asText());
                }
            });

            log.info("Extracted {} preferences from conversation", result.size());
            return Map.copyOf(result);

        } catch (Exception e) {
            log.warn("Failed to extract preferences", e);
            return Map.of();
        }
    }

    private String extractText(ConversationMessage message) {
        return message.blocks().stream()
            .filter(b -> b instanceof TextBlock)
            .map(b -> ((TextBlock) b).text())
            .filter(t -> t != null && !t.isBlank())
            .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
    }
}
```

- [ ] **Step 2: 编写测试**

```java
package com.openmanus.saa.service.context;

import com.openmanus.saa.model.session.ConversationMessage;
import com.openmanus.saa.model.session.MessageRole;
import com.openmanus.saa.model.session.TextBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PreferenceExtractorTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @Test
    void extractPreferences_withUserPreferences_shouldReturnMap() {
        // Setup mock
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("""
            ```json
            {"preferences": {"diet": ["喜欢吃辣"], "budget": "中等"}}
            ```
            """);

        PreferenceExtractor extractor = new PreferenceExtractor(chatClient);

        List<ConversationMessage> messages = List.of(
            ConversationMessage.userText("我喜欢吃辣，预算中等")
        );

        Map<String, Object> prefs = extractor.extractPreferences(messages, "旅行计划");

        assertNotNull(prefs);
        assertTrue(prefs.containsKey("diet"));
    }

    @Test
    void extractPreferences_emptyMessages_shouldReturnEmptyMap() {
        PreferenceExtractor extractor = new PreferenceExtractor(chatClient);
        Map<String, Object> prefs = extractor.extractPreferences(List.of(), null);
        assertTrue(prefs.isEmpty());
    }
}
```

- [ ] **Step 3: 运行测试验证**

Run: `mvn test -pl openmanus-spring-ai-alibaba -Dtest=PreferenceExtractorTest -q`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/openmanus/saa/service/context/PreferenceExtractor.java src/test/java/com/openmanus/saa/service/context/PreferenceExtractorTest.java
git commit -m "feat: add PreferenceExtractor for extracting user preferences from conversation"
```

---

## Task 7: 在 WorkflowService 中更新工作流状态

**Files:**
- Modify: `src/main/java/com/openmanus/saa/service/WorkflowService.java`

- [ ] **Step 1: 在执行完成后更新工作流状态**

找到 `finalizeSuccessfulExecution` 方法，添加状态保存：

```java
Map<String, Object> finalizeSuccessfulExecution(
    String sessionId,
    String planId,
    String objective,
    List<WorkflowStep> steps,
    ResponseMode responseMode,
    IntentResolution intentResolution
) {
    // ... existing code ...

    WorkflowExecutionResponse response = createCompletedResponse(objective, steps, responseMode, outputEvaluation, intentResolution);

    // 更新工作流状态为 COMPLETED
    String deliverable = response.deliverable();
    sessionMemoryService.saveWorkflowState(sessionId, WorkflowState.completed(objective, planId, deliverable));

    return Map.of(GRAPH_RESPONSE_KEY, response);
}
```

- [ ] **Step 2: 在暂停时保存状态（已在 SessionMemoryService.savePendingFeedback 中实现）**

确认 `createPausedResponse` 调用了 `sessionMemoryService.savePendingFeedback`。

- [ ] **Step 3: 在失败时保存状态**

找到 `createFailedResponse` 方法，确保保存失败状态：

```java
WorkflowExecutionResponse createFailedResponse(String objective, List<WorkflowStep> steps, WorkflowStep failedStep) {
    // ... existing code ...

    // 注意：失败状态不需要额外保存，因为不会恢复
    // 但如果有部分成功的步骤，可以保存

    return response;
}
```

- [ ] **Step 4: 运行编译验证**

Run: `mvn compile -pl openmanus-spring-ai-alibaba -q`
Expected: SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openmanus/saa/service/WorkflowService.java
git commit -m "feat: save workflow state on execution completion"
```

---

## Task 8: 实现 CONTINUE 意图识别

**Files:**
- Create: `src/main/java/com/openmanus/saa/service/intent/SessionContextIntentRecognizer.java`
- Modify: `src/main/java/com/openmanus/saa/model/IntentRouteMode.java`

- [ ] **Step 1: 添加 CONTINUE 路由模式**

```java
public enum IntentRouteMode {
    DIRECT_CHAT,
    PLAN_EXECUTE,
    MULTI_AGENT,
    CONTINUE      // 继续暂停的工作流
}
```

- [ ] **Step 2: 创建会话上下文识别器**

```java
package com.openmanus.saa.service.intent;

import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.IntentRouteMode;
import com.openmanus.saa.model.context.WorkflowState;
import com.openmanus.saa.model.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Optional;

/**
 * 识别用户是否要继续暂停的工作流。
 * 最高优先级，先于其他识别器检查。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SessionContextIntentRecognizer implements IntentRecognizer {

    private static final Logger log = LoggerFactory.getLogger(SessionContextIntentRecognizer.class);
    private static final String WORKFLOW_STATE_KEY = "workflowState";

    @Override
    public Optional<IntentResolution> recognize(String prompt, Session session) {
        if (session == null) {
            return Optional.empty();
        }

        WorkflowState state = session.getMemory(WORKFLOW_STATE_KEY, WorkflowState.class)
            .orElse(WorkflowState.none());

        // 检查是否有暂停的工作流
        if (state.isPaused()) {
            log.info("Detected paused workflow for session, routing to CONTINUE");
            return Optional.of(new IntentResolution(
                "continue_paused_workflow",
                1.0,
                IntentRouteMode.CONTINUE,
                null,
                Map.of("workflowState", state),
                null
            ));
        }

        return Optional.empty();
    }
}
```

- [ ] **Step 3: 在 IntentResolutionService 中处理 CONTINUE 模式**

修改 `IntentResolutionService.resolve` 方法：

```java
public IntentResolution resolve(String prompt, Session session, AgentRequest request) {
    IntentResolution resolution = resolve(prompt, session);

    // CONTINUE 模式不需要覆盖
    if (resolution.routeMode() == IntentRouteMode.CONTINUE) {
        return resolution;
    }

    // ... existing override logic ...
}
```

- [ ] **Step 4: 运行编译验证**

Run: `mvn compile -pl openmanus-spring-ai-alibaba -q`
Expected: SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openmanus/saa/service/intent/SessionContextIntentRecognizer.java src/main/java/com/openmanus/saa/model/IntentRouteMode.java
git commit -m "feat: add CONTINUE intent for paused workflow recovery"
```

---

## Task 9: 在 AgentController 中集成上下文感知

**Files:**
- Modify: `src/main/java/com/openmanus/saa/controller/AgentController.java`

- [ ] **Step 1: 注入 ConversationContextFactory**

```java
private final ConversationContextFactory contextFactory;

public AgentController(
    ManusAgentService manusAgentService,
    PlanningService planningService,
    WorkflowService workflowService,
    HumanFeedbackResolutionService humanFeedbackResolutionService,
    ConversationContextFactory contextFactory
) {
    // ... existing assignments ...
    this.contextFactory = contextFactory;
}
```

- [ ] **Step 2: 在 chat 方法中使用上下文**

```java
@PostMapping("/chat")
public AgentResponse chat(@Valid @RequestBody AgentRequest request) {
    // 构建上下文
    ConversationContext context = contextFactory.create(request.sessionId(), request.prompt());

    // 检查是否有暂停的工作流
    if (context.hasPausedWorkflow()) {
        log.info("Routing /chat request as workflow feedback for session {}", request.sessionId());
        HumanFeedbackRequest pendingFeedback = context.workflowState().pendingFeedback();
        WorkflowFeedbackRequest feedbackRequest = new WorkflowFeedbackRequest(
            request.sessionId(),
            null,
            request.prompt(),
            null,
            null
        );
        HumanFeedbackResponse feedback = humanFeedbackResolutionService.resolve(feedbackRequest, pendingFeedback);
        return workflowService.submitHumanFeedbackAsAgentResponse(request.sessionId(), feedback);
    }

    // 正常路由
    return manusAgentService.routeChatWith(request, context);
}
```

- [ ] **Step 3: 运行编译验证**

Run: `mvn compile -pl openmanus-spring-ai-alibaba -q`
Expected: SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/openmanus/saa/controller/AgentController.java
git commit -m "feat: integrate conversation context in AgentController"
```

---

## Task 10: 最终集成测试

**Files:**
- Create: `src/test/java/com/openmanus/saa/integration/ConversationContextIntegrationTest.java`

- [ ] **Step 1: 编写集成测试**

```java
package com.openmanus.saa.integration;

import com.openmanus.saa.model.AgentRequest;
import com.openmanus.saa.model.AgentResponse;
import com.openmanus.saa.model.context.WorkflowState;
import com.openmanus.saa.model.session.Session;
import com.openmanus.saa.service.session.SessionMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ConversationContextIntegrationTest {

    @Autowired
    private SessionMemoryService sessionMemoryService;

    @Test
    void testUserPreferencePersistence() {
        String sessionId = "test-pref-session";

        // 保存偏好
        sessionMemoryService.saveUserPreference(sessionId, "diet", "喜欢吃辣");

        // 验证偏好持久化
        var pref = sessionMemoryService.getUserPreference(sessionId, "diet");
        assertTrue(pref.isPresent());
        assertEquals("喜欢吃辣", pref.get());
    }

    @Test
    void testWorkflowStatePersistence() {
        String sessionId = "test-workflow-session";

        // 保存工作流状态
        WorkflowState state = WorkflowState.completed("旅行计划", "plan-123", "详细行程");
        sessionMemoryService.saveWorkflowState(sessionId, state);

        // 验证状态持久化
        var retrieved = sessionMemoryService.getWorkflowState(sessionId);
        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get().isCompleted());
        assertEquals("详细行程", retrieved.get().lastDeliverable());
    }
}
```

- [ ] **Step 2: 运行所有测试**

Run: `mvn test -pl openmanus-spring-ai-alibaba -q`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/openmanus/saa/integration/ConversationContextIntegrationTest.java
git commit -m "test: add integration tests for conversation context"
```

---

## 完成检查

- [ ] 所有新文件已创建
- [ ] 所有测试通过
- [ ] 代码风格符合项目规范
- [ ] 向后兼容性已验证
- [ ] 文档已更新（如有需要）
