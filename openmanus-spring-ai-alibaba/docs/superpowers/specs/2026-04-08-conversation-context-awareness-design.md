# 对话上下文感知系统设计

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现对话上下文感知，让 Agent 能够理解用户意图的连续性，包括：补充信息、进一步要求、新内容处理，以及用户偏好的继承。

**Architecture:**
- 创建 `ConversationContext` 统一上下文对象，在入口处一次性构建
- 在 `Session.workingMemory` 中存储用户偏好和工作流状态
- 利用 Graph 的 Checkpoint 机制（MemorySaver + threadId）实现工作流状态的会话级持久化
- 在 Planning/执行流程中注入对话历史和用户偏好

**Tech Stack:** Java 17+, Spring AI, spring-ai-alibaba-graph

---

## 1. 问题背景

当前系统存在以下问题：

1. **对话历史丢失**：PLAN_EXECUTE 模式没有传递对话历史，用户说"帮我查北京天气"后再说"适合旅游吗"，Agent 无法关联上下文
2. **用户偏好不持久**：用户在某次对话中提到的偏好（如"我喜欢吃辣"）不会影响后续回答
3. **工作流状态未持久化**：`SessionMemoryService.getPendingFeedback()` 永远返回空，导致 HumanFeedback 机制无法正常工作

### 用户场景示例

```
用户: "我喜欢吃辣"
用户: "帮我制定南京7日旅行计划"
  → 系统应记住"喜欢吃辣"偏好

用户: "去北京吃什么"
  → 系统应结合"喜欢吃辣"回答

用户: "帮我查北京天气"
用户: "适合旅游吗？"
  → 系统应能关联"查天气"的结果来回答
```

---

## 2. 设计方案

### 2.1 核心数据结构

#### ConversationContext（统一上下文对象）

```java
public record ConversationContext(
    // 会话标识
    String sessionId,
    Session session,

    // 对话历史
    String conversationHistory,           // 最近 N 轮对话摘要
    List<ConversationMessage> messages,  // 完整消息列表

    // 用户偏好（从 workingMemory 提取）
    Map<String, Object> userPreferences,

    // 工作流状态（从 Graph Checkpoint 或 workingMemory 获取）
    WorkflowState workflowState,

    // 当前请求
    String currentPrompt
) {
    public static ConversationContext from(Session session, String currentPrompt) {
        // 构建逻辑
    }
}
```

#### WorkflowState（工作流状态）

```java
public record WorkflowState(
    WorkflowStatus status,           // PAUSED, COMPLETED, FAILED, NONE
    String objective,                // 原始目标
    String planId,                   // 计划 ID
    int currentStepIndex,            // 当前步骤索引
    List<WorkflowStep> steps,       // 步骤列表
    HumanFeedbackRequest pendingFeedback,  // 待处理反馈
    String lastDeliverable,          // 上一次的交付内容
    Instant lastActiveAt             // 最后活跃时间
) {
    public enum WorkflowStatus {
        NONE,           // 无工作流
        IN_PROGRESS,    // 执行中
        PAUSED,         // 暂停等待反馈
        COMPLETED,      // 已完成
        FAILED          // 失败
    }
}
```

#### Session.workingMemory 结构

```java
// Key: "userPreferences"
{
    "diet": ["喜欢吃辣", "不吃香菜"],
    "budget": "适中",
    "travel_style": "深度游",
    "language": "中文"
}

// Key: "workflowState"
{
    "status": "COMPLETED",
    "objective": "南京7日旅行计划",
    "planId": "workflow-xxx",
    "currentStepIndex": 5,
    "lastDeliverable": "...",
    "lastActiveAt": "2026-04-08T10:00:00Z"
}
```

### 2.2 数据流

```
用户请求 POST /chat
    ↓
AgentController.chat()
    ↓
1. 获取 Session
2. 构建 ConversationContext（一次性）
   ├── conversationHistory: sessionMemoryService.summarizeHistory()
   ├── userPreferences: session.workingMemory["userPreferences"]
   └── workflowState: session.workingMemory["workflowState"]
    ↓
3. IntentResolutionService.resolve(prompt, context)
   ├─ 检查 workflowState.status
   │   ├─ PAUSED → 返回 CONTINUE 意图，触发 HumanFeedback 流程
   │   ├─ COMPLETED → 检查与当前 prompt 的关联度
   │   └─ NONE → 正常意图识别
   └─ 返回 IntentResolution
    ↓
4. 根据意图执行
   ├─ CONTINUE → 调用 HumanFeedbackResolutionService
   ├─ DIRECT_CHAT → ManusAgentService.chat(context)
   └─ PLAN_EXECUTE → WorkflowService.execute(context)
    ↓
5. 执行完成后更新 Session
   ├─ 提取用户偏好：LLM 分析当前对话，提取偏好
   └─ 更新工作流状态
    ↓
6. 保存 Session
```

### 2.3 关键组件设计

#### SessionMemoryService 新增方法

```java
// 偏好相关
void saveUserPreference(String sessionId, String key, Object value);
Optional<Object> getUserPreference(String sessionId, String key);
Map<String, Object> extractPreferencesFromMessages(Session session);

// 工作流状态相关
void saveWorkflowState(String sessionId, WorkflowState state);
Optional<WorkflowState> getWorkflowState(String sessionId);
void clearWorkflowState(String sessionId);

// 对话历史摘要
String summarizeHistory(Session session, int maxMessages);
String summarizeHistoryWithPreferences(Session session, int maxMessages);
```

#### PreferenceExtractor（偏好提取器）

在每次执行完成后，调用 LLM 提取用户偏好：

```java
@Component
public class PreferenceExtractor {

    public Map<String, Object> extractPreferences(
        List<ConversationMessage> messages,
        String currentObjective
    ) {
        // 调用 LLM 分析对话，提取偏好
        // 返回 Map<String, Object> 存储到 workingMemory
    }
}
```

### 2.4 Graph Checkpoint 与 Session 的关系

已实现的 Graph 会话记忆（MemorySaver + threadId）用于：

| 用途 | 说明 |
|------|------|
| 工作流执行状态 | 当前执行到哪个步骤、步骤结果等 |
| 断点恢复 | 工作流暂停后，通过 Checkpoint 恢复执行 |
| 并发隔离 | 不同 sessionId 的状态互不干扰 |

**注意**：Graph Checkpoint 保存的是执行状态，Session 保存的是更高层的业务状态（如用户偏好）。

---

## 3. 实现步骤

### Phase 1: 基础数据结构

1. 创建 `ConversationContext` record
2. 创建 `WorkflowState` record
3. 修改 `Session` 相关方法支持 workingMemory 操作

### Phase 2: SessionMemoryService 增强

1. 实现 `saveUserPreference` / `getUserPreference`
2. 实现 `saveWorkflowState` / `getWorkflowState`
3. 完善 `summarizeHistory` 方法

### Phase 3: 对话历史注入

1. 创建 `ConversationContextFactory`
2. 在 `ManusAgentService.chat()` 中传递上下文
3. 在 `PlanningService` 中注入对话历史

### Phase 4: 用户偏好提取

1. 创建 `PreferenceExtractor` 组件
2. 在执行完成后调用提取器
3. 将偏好保存到 workingMemory

### Phase 5: 工作流状态感知

1. 在 `IntentResolutionService` 中检查 workflowState
2. 实现 CONTINUE 意图识别
3. 与 HumanFeedback 流程集成

---

## 4. 测试场景

### 场景 1: 偏好继承

```
用户: "我喜欢吃辣" → 保存偏好: diet=["喜欢吃辣"]
用户: "去北京吃什么" → 回答应包含辣味餐厅
```

### 场景 2: 上下文关联

```
用户: "帮我查北京天气" → 返回天气信息
用户: "适合旅游吗？" → 结合天气信息回答
```

### 场景 3: 工作流继续

```
用户: "帮我制定南京旅行计划" → 工作流暂停等待参数
用户: "3月15日到3月22日" → 继续执行，生成完整计划
```

### 场景 4: HumanFeedback 恢复

```
步骤执行失败 → 暂停，等待反馈
用户: "用新的API key" → 继续执行
```

---

## 5. 向后兼容

1. **不改变现有 API 签名**：新增方法，保留现有方法
2. **Session 不可变**：使用 withXxx 方法创建新实例
3. **可选功能**：如果 workingMemory 为空，系统降级为无偏好模式
