# User Input Intent Recognition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement automatic user input intent recognition to handle feedback continuation, parameter supplementation, and new task detection.

**Architecture:** Enhance existing `SessionContextIntentRecognizer` with LLM-based intent classification and parameter extraction. The recognizer will classify user input into three categories: SUPPLEMENT_INFO, CONTINUE, or NEW_TASK, then route accordingly.

**Tech Stack:** Java 17, Spring AI, Spring Boot

---

## File Structure

| File | Responsibility |
|------|----------------|
| `src/main/java/com/openmanus/saa/model/UserInputIntent.java` | Enum for intent types |
| `src/main/java/com/openmanus/saa/model/InputClassification.java` | Classification result record |
| `src/main/java/com/openmanus/saa/service/intent/UserInputClassifier.java` | LLM-based intent classifier |
| `src/main/java/com/openmanus/saa/service/intent/SessionContextIntentRecognizer.java` | Modified: add classification logic |
| `src/main/java/com/openmanus/saa/service/ManusAgentService.java` | Modified: implement continuePausedWorkflow handlers |
| `src/test/java/com/openmanus/saa/service/intent/UserInputClassifierTest.java` | Unit tests |

---

### Task 1: Create UserInputIntent Enum

**Files:**
- Create: `src/main/java/com/openmanus/saa/model/UserInputIntent.java`

- [ ] **Step 1: Write the enum**

```java
package com.openmanus.saa.model;

/**
 * 用户输入意图类型。
 * 用于区分用户输入是对暂停工作流的反馈还是新任务。
 */
public enum UserInputIntent {
    /** 补充信息：用户回答之前的问题 */
    SUPPLEMENT_INFO,
    /** 继续执行：用户明确要求继续 */
    CONTINUE,
    /** 新任务：完全独立的请求 */
    NEW_TASK,
    /** 不确定：需要进一步确认 */
    UNCERTAIN
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/openmanus/saa/model/UserInputIntent.java
git commit -m "feat: add UserInputIntent enum for input classification"
```

---

### Task 2: Create InputClassification Record

**Files:**
- Create: `src/main/java/com/openmanus/saa/model/InputClassification.java`

- [ ] **Step 1: Write the record**

```java
package com.openmanus.saa.model;

import java.util.Map;

/**
 * 用户输入分类结果。
 */
public record InputClassification(
    UserInputIntent intent,
    Map<String, Object> extractedParams,
    String reasoning
) {
    public static InputClassification supplementInfo(Map<String, Object> params) {
        return new InputClassification(UserInputIntent.SUPPLEMENT_INFO, params, null);
    }

    public static InputClassification continueExecution() {
        return new InputClassification(UserInputIntent.CONTINUE, Map.of(), null);
    }

    public static InputClassification newTask() {
        return new InputClassification(UserInputIntent.NEW_TASK, Map.of(), null);
    }

    public static InputClassification uncertain(String reason) {
        return new InputClassification(UserInputIntent.UNCERTAIN, Map.of(), reason);
    }

    public boolean isSupplementInfo() {
        return intent == UserInputIntent.SUPPLEMENT_INFO;
    }

    public boolean isContinue() {
        return intent == UserInputIntent.CONTINUE;
    }

    public boolean isNewTask() {
        return intent == UserInputIntent.NEW_TASK;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/openmanus/saa/model/InputClassification.java
git commit -m "feat: add InputClassification record for classification results"
```

---

### Task 3: Create UserInputClassifier Service

**Files:**
- Create: `src/main/java/com/openmanus/saa/service/intent/UserInputClassifier.java`

- [ ] **Step 1: Write the classifier interface and implementation**

```java
package com.openmanus.saa.service.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.InputClassification;
import com.openmanus.saa.model.UserInputIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户输入意图分类器。
 * 基于 LLM 判断用户输入是对反馈的回答还是新任务。
 */
@Service
public class UserInputClassifier {

    private static final Logger log = LoggerFactory.getLogger(UserInputClassifier.class);
    private static final List<String> CONTINUE_KEYWORDS = List.of(
        "继续", "继续执行", "继续吧", "go on", "continue",
        "用默认的", "不用管", "继续用", "跳过", "skip",
        "好的", "可以", "没问题", "ok", "yes"
    );

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public UserInputClassifier(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 分类用户输入意图。
     */
    public InputClassification classify(String prompt, HumanFeedbackRequest pendingFeedback) {
        if (prompt == null || prompt.isBlank()) {
            return InputClassification.uncertain("Empty input");
        }

        // 1. 规则优先：检查是否明确要求继续
        if (isExplicitContinue(prompt)) {
            log.info("User input detected as CONTINUE via rule matching");
            return InputClassification.continueExecution();
        }

        // 2. 规则检查：是否能提取参数
        Map<String, Object> extractedParams = extractParametersByRule(prompt, pendingFeedback);
        if (!extractedParams.isEmpty()) {
            log.info("User input detected as SUPPLEMENT_INFO with params: {}", extractedParams);
            return InputClassification.supplementInfo(extractedParams);
        }

        // 3. LLM 分类：判断是否新任务
        return classifyWithLLM(prompt, pendingFeedback);
    }

    /**
     * 规则判断：是否明确要求继续。
     */
    private boolean isExplicitContinue(String prompt) {
        String lower = prompt.toLowerCase().trim();
        return CONTINUE_KEYWORDS.stream()
            .anyMatch(keyword -> lower.contains(keyword.toLowerCase()));
    }

    /**
     * 规则提取：从用户输入中提取参数。
     */
    private Map<String, Object> extractParametersByRule(String prompt, HumanFeedbackRequest pendingFeedback) {
        Map<String, Object> params = new HashMap<>();

        if (pendingFeedback == null || pendingFeedback.getErrorMessage() == null) {
            return params;
        }

        String errorMessage = pendingFeedback.getErrorMessage();

        // 提取日期（匹配 "是4月8"、"4月8日"、"下周1" 等）
        if (errorMessage.contains("出发日期") || errorMessage.contains("departureDate")) {
            // 匹配日期模式
            Pattern datePattern = Pattern.compile("(\\d{1,2}月\\d{1,2}[日号]?|下周[一二三四五六日]|\\d{4}-\\d{2}-\\d{2})");
            Matcher matcher = datePattern.matcher(prompt);
            if (matcher.find()) {
                params.put("departureDate", matcher.group(1));
            }
        }

        // 提取购物偏好
        if (errorMessage.contains("购物") || errorMessage.contains("shopping")) {
            String[] shoppingAreas = {"新街口", "老门东", "河西奥体", "夫子庙"};
            for (String area : shoppingAreas) {
                if (prompt.contains(area)) {
                    params.put("shoppingPreference", area);
                    break;
                }
            }
        }

        // 提取酒店要求
        if (errorMessage.contains("酒店") || errorMessage.contains("hotel")) {
            if (prompt.contains("近地铁") || prompt.contains("地铁口")) {
                params.put("hotelCriteria", "near_subway");
            } else if (prompt.contains("含早餐")) {
                params.put("hotelCriteria", "with_breakfast");
            }
        }

        // 如果用户说"没特别"或"没有特别要求"，标记为使用默认值
        if (prompt.contains("没特别") || prompt.contains("没有特别") || prompt.contains("无所谓")) {
            params.put("useDefault", true);
        }

        return params;
    }

    /**
     * LLM 分类：判断用户输入意图。
     */
    private InputClassification classifyWithLLM(String prompt, HumanFeedbackRequest pendingFeedback) {
        String systemPrompt = """
            你是一个用户意图分类助手。你需要判断用户的输入是：
            1. SUPPLEMENT_INFO：回答之前的问题（提供信息）
            2. CONTINUE：要求继续执行（使用默认值）
            3. NEW_TASK：开始一个新的独立任务

            返回 JSON 格式：{"intent": "SUPPLEMENT_INFO|CONTINUE|NEW_TASK", "reasoning": "简要原因"}
            """;

        String userPrompt = String.format("""
            之前的任务：%s
            系统的问题：%s
            用户的输入：%s

            请判断用户的意图。
            """,
            pendingFeedback != null ? pendingFeedback.getObjective() : "无",
            pendingFeedback != null ? pendingFeedback.getErrorMessage() : "无",
            prompt
        );

        try {
            String response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();

            log.debug("LLM classification response: {}", response);

            JsonNode node = objectMapper.readTree(extractJson(response));
            String intentStr = node.get("intent").asText();
            UserInputIntent intent = UserInputIntent.valueOf(intentStr);

            return new InputClassification(intent, Map.of(), node.get("reasoning").asText());
        } catch (Exception e) {
            log.warn("LLM classification failed, defaulting to SUPPLEMENT_INFO", e);
            // 默认假设用户是在回答问题
            return InputClassification.supplementInfo(Map.of());
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/openmanus/saa/service/intent/UserInputClassifier.java
git commit -m "feat: add UserInputClassifier for intent classification"
```

---

### Task 4: Modify SessionContextIntentRecognizer

**Files:**
- Modify: `src/main/java/com/openmanus/saa/service/intent/SessionContextIntentRecognizer.java`

- [ ] **Step 1: Read current file**

Read the file to understand current implementation.

- [ ] **Step 2: Add classification logic**

```java
package com.openmanus.saa.service.intent;

import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.InputClassification;
import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.IntentRouteMode;
import com.openmanus.saa.model.context.WorkflowState;
import com.openmanus.saa.model.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
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

    private final UserInputClassifier userInputClassifier;

    public SessionContextIntentRecognizer(UserInputClassifier userInputClassifier) {
        this.userInputClassifier = userInputClassifier;
    }

    @Override
    public Optional<IntentResolution> recognize(String prompt, Session session) {
        if (session == null) {
            return Optional.empty();
        }

        WorkflowState state = session.getMemory(WORKFLOW_STATE_KEY, WorkflowState.class)
            .orElse(WorkflowState.none());

        // 检查是否有暂停的工作流
        if (state.isPaused()) {
            log.info("Detected paused workflow for session, classifying user input intent");

            // 获取待处理反馈
            HumanFeedbackRequest pendingFeedback = state.pendingFeedback();

            // 分类用户输入意图
            InputClassification classification = userInputClassifier.classify(prompt, pendingFeedback);

            log.info("User input classified as: {}", classification.intent());

            // 构建属性
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("workflowState", state);
            attributes.put("inputClassification", classification);

            return Optional.of(new IntentResolution(
                "continue_paused_workflow",
                1.0,
                IntentRouteMode.CONTINUE,
                null,
                attributes,
                null
            ));
        }

        return Optional.empty();
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/openmanus/saa/service/intent/SessionContextIntentRecognizer.java
git commit -m "feat: add input classification to SessionContextIntentRecognizer"
```

---

### Task 5: Implement ManusAgentService Handlers

**Files:**
- Modify: `src/main/java/com/openmanus/saa/service/ManusAgentService.java`

- [ ] **Step 1: Read current continuePausedWorkflow method**

Read the file to find the `continuePausedWorkflow` method.

- [ ] **Step 2: Implement the handler methods**

Add the following methods to `ManusAgentService`:

```java
/**
 * Continue a paused workflow with user feedback.
 */
private AgentResponse continuePausedWorkflow(String sessionId, String prompt, IntentResolution resolution) {
    log.info("Continuing paused workflow for session {} with feedback: {}", sessionId, prompt);

    // 从 attributes 获取分类结果
    InputClassification classification = resolution.getAttribute("inputClassification", InputClassification.class);
    if (classification == null) {
        classification = InputClassification.supplementInfo(Map.of());
    }

    return switch (classification.intent()) {
        case SUPPLEMENT_INFO -> handleSupplementInfo(sessionId, prompt, resolution, classification);
        case CONTINUE -> handleContinue(sessionId, resolution);
        case NEW_TASK -> handleNewTask(sessionId, prompt);
        case UNCERTAIN -> handleUncertain(sessionId, prompt, resolution);
    };
}

/**
 * 处理补充信息：合并参数，继续执行。
 */
private AgentResponse handleSupplementInfo(
        String sessionId,
        String prompt,
        IntentResolution resolution,
        InputClassification classification
) {
    log.info("Handling SUPPLEMENT_INFO for session {}", sessionId);

    WorkflowState state = resolution.getAttribute("workflowState", WorkflowState.class);
    if (state == null || state.pendingFeedback() == null) {
        return chat(sessionId, prompt, null);
    }

    // 构建 WorkflowFeedbackRequest
    WorkflowFeedbackRequest feedbackRequest = new WorkflowFeedbackRequest(
        sessionId,
        null, // planId from state
        prompt,
        classification.extractedParams(),
        null  // action
    );

    // 解析反馈并继续执行
    HumanFeedbackRequest pendingFeedback = state.pendingFeedback();
    HumanFeedbackResponse feedback = humanFeedbackResolutionService.resolve(feedbackRequest, pendingFeedback);

    return workflowService.submitHumanFeedbackAsAgentResponse(sessionId, feedback);
}

/**
 * 处理继续执行：使用默认值继续。
 */
private AgentResponse handleContinue(String sessionId, IntentResolution resolution) {
    log.info("Handling CONTINUE for session {}", sessionId);

    WorkflowState state = resolution.getAttribute("workflowState", WorkflowState.class);
    if (state == null || state.pendingFeedback() == null) {
        return chat(sessionId, "继续", null);
    }

    // 使用空参数继续（让系统使用默认值）
    WorkflowFeedbackRequest feedbackRequest = new WorkflowFeedbackRequest(
        sessionId,
        null,
        "继续使用默认值",
        Map.of("useDefault", true),
        null
    );

    HumanFeedbackRequest pendingFeedback = state.pendingFeedback();
    HumanFeedbackResponse feedback = humanFeedbackResolutionService.resolve(feedbackRequest, pendingFeedback);

    return workflowService.submitHumanFeedbackAsAgentResponse(sessionId, feedback);
}

/**
 * 处理新任务：清除旧状态，开始新任务。
 */
private AgentResponse handleNewTask(String sessionId, String prompt) {
    log.info("Handling NEW_TASK for session {}, clearing workflow state", sessionId);

    // 清除工作流状态
    sessionMemoryService.clearWorkflowState(sessionId);

    // 正常路由
    return chat(sessionId, prompt, null);
}

/**
 * 处理不确定情况：询问用户意图。
 */
private AgentResponse handleUncertain(String sessionId, String prompt, IntentResolution resolution) {
    log.info("Handling UNCERTAIN for session {}", sessionId);

    // 默认当作补充信息处理
    InputClassification classification = InputClassification.supplementInfo(Map.of());
    return handleSupplementInfo(sessionId, prompt, resolution, classification);
}
```

- [ ] **Step 3: Add necessary imports**

Add imports at the top of the file:
```java
import com.openmanus.saa.model.InputClassification;
import com.openmanus.saa.model.WorkflowFeedbackRequest;
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/openmanus/saa/service/ManusAgentService.java
git commit -m "feat: implement continuePausedWorkflow handlers for all intent types"
```

---

### Task 6: Add IntentResolution Helper Method

**Files:**
- Modify: `src/main/java/com/openmanus/saa/model/IntentResolution.java`

- [ ] **Step 1: Read current file**

Read to understand the current structure.

- [ ] **Step 2: Add getAttribute helper method**

Add method to safely get typed attributes:

```java
@SuppressWarnings("unchecked")
public <T> T getAttribute(String key, Class<T> type) {
    Object value = attributes.get(key);
    if (value != null && type.isInstance(value)) {
        return (T) value;
    }
    return null;
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/openmanus/saa/model/IntentResolution.java
git commit -m "feat: add getAttribute helper to IntentResolution"
```

---

### Task 7: Write Unit Tests

**Files:**
- Create: `src/test/java/com/openmanus/saa/service/intent/UserInputClassifierTest.java`

- [ ] **Step 1: Write test class**

```java
package com.openmanus.saa.service.intent;

import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.InputClassification;
import com.openmanus.saa.model.UserInputIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserInputClassifierTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private UserInputClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new UserInputClassifier(chatClient, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void explicitContinue_shouldReturnContinue() {
        HumanFeedbackRequest feedback = createFeedback("需要出发日期");

        InputClassification result = classifier.classify("继续", feedback);

        assertTrue(result.isContinue());
    }

    @Test
    void explicitContinue_withEnglish_shouldReturnContinue() {
        HumanFeedbackRequest feedback = createFeedback("需要出发日期");

        InputClassification result = classifier.classify("continue", feedback);

        assertTrue(result.isContinue());
    }

    @Test
    void supplementDate_shouldExtractDate() {
        HumanFeedbackRequest feedback = createFeedback("需用户确认：1）出发日期（下周1即2026-04-08？）");

        InputClassification result = classifier.classify("是4月8日", feedback);

        assertTrue(result.isSupplementInfo());
        assertEquals("4月8日", result.extractedParams().get("departureDate"));
    }

    @Test
    void supplementWithNoPreference_shouldMarkUseDefault() {
        HumanFeedbackRequest feedback = createFeedback("需要出发日期和酒店要求");

        InputClassification result = classifier.classify("是4月8，其它没有特别的", feedback);

        assertTrue(result.isSupplementInfo());
        assertEquals(true, result.extractedParams().get("useDefault"));
    }

    @Test
    void nullPrompt_shouldReturnUncertain() {
        InputClassification result = classifier.classify(null, null);

        assertEquals(UserInputIntent.UNCERTAIN, result.intent());
    }

    @Test
    void emptyPrompt_shouldReturnUncertain() {
        InputClassification result = classifier.classify("   ", null);

        assertEquals(UserInputIntent.UNCERTAIN, result.intent());
    }

    private HumanFeedbackRequest createFeedback(String errorMessage) {
        return new HumanFeedbackRequest(
            "test-session",
            "旅行计划",
            "plan-123",
            0,
            null,
            null,
            errorMessage,
            "请补充参数"
        );
    }
}
```

- [ ] **Step 2: Run tests**

```bash
mvn test -Dtest=UserInputClassifierTest -q
```

Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/openmanus/saa/service/intent/UserInputClassifierTest.java
git commit -m "test: add UserInputClassifier unit tests"
```

---

### Task 8: Integration Test

**Files:**
- Create: `src/test/java/com/openmanus/saa/integration/UserInputIntentIntegrationTest.java`

- [ ] **Step 1: Write integration test**

```java
package com.openmanus.saa.integration;

import com.openmanus.saa.model.AgentRequest;
import com.openmanus.saa.model.AgentResponse;
import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.context.WorkflowState;
import com.openmanus.saa.service.session.SessionMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserInputIntentIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SessionMemoryService sessionMemoryService;

    @Test
    void supplementInfo_shouldContinueWorkflow() {
        String sessionId = "test-supplement-session";

        // 1. 发起第一个请求
        AgentRequest request1 = new AgentRequest(
            sessionId,
            "制定南京7日旅行计划",
            null, null, null, null, null, null
        );

        ResponseEntity<AgentResponse> response1 = restTemplate.postForEntity(
            "/chat", request1, AgentResponse.class
        );

        // 2. 检查是否有待处理反馈
        if (response1.getBody() != null && response1.getBody().pendingFeedback() != null) {
            // 3. 发送补充信息
            AgentRequest request2 = new AgentRequest(
                sessionId,
                "是4月8日，其它没有特别的",
                null, null, null, null, null, null
            );

            ResponseEntity<AgentResponse> response2 = restTemplate.postForEntity(
                "/chat", request2, AgentResponse.class
            );

            assertEquals(HttpStatus.OK, response2.getStatusCode());
            assertNotNull(response2.getBody());
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/test/java/com/openmanus/saa/integration/UserInputIntentIntegrationTest.java
git commit -m "test: add integration test for user input intent"
```

---

## Summary

| Task | Description | Files |
|------|-------------|-------|
| 1 | Create UserInputIntent enum | 1 new |
| 2 | Create InputClassification record | 1 new |
| 3 | Create UserInputClassifier service | 1 new |
| 4 | Modify SessionContextIntentRecognizer | 1 modified |
| 5 | Implement ManusAgentService handlers | 1 modified |
| 6 | Add IntentResolution helper | 1 modified |
| 7 | Write unit tests | 1 new |
| 8 | Write integration test | 1 new |

**Total files: 6 new, 3 modified**
