# 响应输出结构重构设计

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 重构 WorkflowExecutionResponse 输出结构，明确区分"执行元数据"和"用户交付内容"，统一多模式输出。

**Architecture:** 
- 将 `deliverable` 字段提升到顶层，专门存放用户请求的最终结果
- 新增 `content` 字段存放详细执行报告
- 简化 `WorkflowSummary`，移除 `userMessage`，合并评估状态
- 删除外层 `outputEvaluation`，合并到 `summary`

**Tech Stack:** Java 17+, Spring AI

---

## 1. 问题背景

当前 `WorkflowExecutionResponse` 存在职责模糊问题：

1. `summary.userMessage` 混合了"执行状态"和"评估说明"
2. 用户期望看到的是"具体的旅行计划"，而非"执行状态报告"
3. 多种输出模式（chat、plan-execute）缺乏统一的输出字段

**用户反馈示例**：
> "目前的summary还是有问题，用户需要的是计划，你告诉了用户完成了，但是没输出计划"

---

## 2. 目标结构

### 2.1 WorkflowExecutionResponse（重构后）

```java
public record WorkflowExecutionResponse(
    String objective,           // 用户原始目标
    String deliverable,         // 最终交付内容（计划、报告、建议等）
    String content,             // 详细执行报告（Markdown格式）
    List<String> artifacts,     // 文件产物路径列表
    WorkflowSummary summary,    // 执行元数据（含评估状态）
    List<WorkflowStep> steps,   // 详细步骤列表
    HumanFeedbackRequest pendingFeedback
)
```

### 2.2 WorkflowSummary（重构后）

```java
public record WorkflowSummary(
    // 执行状态
    WorkflowExecutionStatus status,
    String statusLabel,
    int totalSteps,
    int completedSteps,
    int skippedSteps,
    int failedSteps,
    boolean requiresHumanFeedback,
    String currentStep,
    
    // 评估状态（合并进来）
    OutputEvaluationStatus evaluationStatus,
    String evaluationLabel,
    String evaluationMessage,
    List<String> evaluationIssues
)
```

---

## 3. 字段职责定义

| 字段 | 所属 | 内容 | 示例 |
|------|------|------|------|
| `objective` | Response | 用户原始目标 | "帮我制定南京7日旅行计划" |
| `deliverable` | Response | 最终交付内容 | 具体的每日行程安排 |
| `content` | Response | 详细执行报告 | Markdown格式的步骤、日志、评估 |
| `artifacts` | Response | 文件产物路径 | ["/workspace/travel_plan.pdf"] |
| `summary` | Response | 执行元数据 | 状态、步数、评估状态 |
| `steps` | Response | 详细步骤 | 每个 step 的执行结果 |
| `pendingFeedback` | Response | 待人工反馈 | 阻塞时的反馈请求 |

### WorkflowSummary 内部字段

| 字段 | 内容 | 示例 |
|------|------|------|
| `status` | 执行状态枚举 | COMPLETED, FAILED |
| `statusLabel` | 状态文本 | "已完成" |
| `evaluationStatus` | 评估状态枚举 | PASSED, MAJOR_ISSUES |
| `evaluationLabel` | 评估文本 | "可用，但仍需进一步补充" |
| `evaluationMessage` | 评估说明 | "景点推荐已获取，但未按7天行程整合..." |
| `evaluationIssues` | 问题列表 | ["酒店结果失真", "缺少交通建议"] |

---

## 4. 生成逻辑

### 4.1 deliverable 生成

**时机**：执行完成后，在 `createCompletedResponse()` 中生成

**来源优先级**：
1. 如果有专门的"整合步骤"（compose step），取其 `step.result`
2. 否则，从所有 `step.toolOutputs` 中提取有意义的数据
3. 最后，取最后一个完成步骤的 `step.result`

**关键改动**：PlanningService 需要在规划时添加"整合步骤"

### 4.2 content 生成

**时机**：执行完成后

**内容**：现有的 `formatWorkflowExecutionMarkdown()` 输出

**结构**：
```markdown
## 执行概览
- 目标：...
- 状态：...
- 评估：...

## 步骤状态
### 步骤 1
- Agent：...
- 结果：...

## 执行日志
- ...

## 输出评估
- ...
```

### 4.3 summary 生成

**时机**：执行过程中更新

**变化**：
- 移除 `userMessage` 字段
- 合并 `evaluationStatus`, `evaluationLabel`, `evaluationMessage`, `evaluationIssues`

---

## 5. 向后兼容

### 5.1 构造函数兼容

```java
// 旧构造函数（deprecated，但保留）
public WorkflowExecutionResponse(
    String objective,
    List<WorkflowStep> steps,
    List<String> artifacts,
    List<String> executionLog,
    WorkflowSummary summary
) {
    this(objective, null, null, artifacts, summary, steps, null);
}

// 新构造函数
public WorkflowExecutionResponse(
    String objective,
    String deliverable,
    String content,
    List<String> artifacts,
    WorkflowSummary summary,
    List<WorkflowStep> steps,
    HumanFeedbackRequest pendingFeedback
)
```

### 5.2 WorkflowSummary 兼容

```java
// 兼容构造函数（无评估字段）
public WorkflowSummary(
    WorkflowExecutionStatus status,
    String statusLabel,
    int totalSteps,
    int completedSteps,
    int skippedSteps,
    int failedSteps,
    boolean requiresHumanFeedback,
    String currentStep
) {
    this(status, statusLabel, totalSteps, completedSteps, skippedSteps,
         failedSteps, requiresHumanFeedback, currentStep,
         null, null, null, null);
}
```

---

## 6. 改动范围

### 6.1 需要修改的文件

| 文件 | 改动 |
|------|------|
| `WorkflowSummary.java` | 移除 userMessage，添加评估字段 |
| `WorkflowExecutionResponse.java` | 添加 deliverable、content，移除 executionLog 和 outputEvaluation |
| `WorkflowService.java` | 修改 createCompletedResponse 等方法 |
| `PlanningService.java` | 添加"整合步骤"规划逻辑 |
| `WorkflowLifecycleNodeHandler.java` | 适配新的响应构建 |

### 6.2 删除的字段

- `WorkflowSummary.userMessage`
- `WorkflowExecutionResponse.executionLog`（合并到 content）
- `WorkflowExecutionResponse.outputEvaluation`（合并到 summary）

---

## 7. 测试验证

1. 执行旅行规划请求，验证 `deliverable` 包含具体行程计划
2. 验证 `content` 包含完整的执行报告
3. 验证 `summary` 正确反映执行状态和评估状态
4. 验证向后兼容性：旧代码仍能编译运行