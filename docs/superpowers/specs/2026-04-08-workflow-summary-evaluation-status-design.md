# WorkflowSummary 评估状态集成设计

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 WorkflowSummary 中集成 OutputEvaluationResult 状态，解决执行状态与评估状态不一致的问题

**Architecture:** 扩展 WorkflowSummary record，添加评估状态字段；修改 buildSummary 和 statusLabel 方法，使评估状态影响最终显示

**Tech Stack:** Java record, Spring Service

---

## 问题背景

当前 `WorkflowSummary.status` 只反映步骤执行状态，不考虑 `OutputEvaluationResult` 的质量评估。导致：
- 所有步骤执行成功 → status = COMPLETED（"完全执行成功"）
- 即使 outputEvaluation.status = MAJOR_ISSUES，状态标签仍显示成功

---

## 设计方案

### 1. WorkflowSummary 扩展

新增两个字段：
- `evaluationStatus`: OutputEvaluationStatus 类型
- `evaluationLabel`: String 类型

### 2. 状态优先级

评估状态优先于执行状态显示：
- BLOCKER → "结果不可用"
- MAJOR_ISSUES → "已完成，需改进"
- 其他 → 使用原有执行状态

### 3. 影响范围

1. **WorkflowSummary.java** - 添加新字段
2. **WorkflowService.java** - 修改 buildSummary 方法
3. **WorkflowService.java** - 修改 statusLabel 方法
4. **formatWorkflowExecutionMarkdown** - 更新输出格式

---

## 任务清单

### Task 1: WorkflowSummary 添加评估状态字段

**Files:**
- Modify: `src/main/java/com/openmanus/saa/model/WorkflowSummary.java`

- [ ] **Step 1: 添加新字段到 record**

```java
public record WorkflowSummary(
    WorkflowExecutionStatus status,
    String statusLabel,
    int totalSteps,
    int completedSteps,
    int skippedSteps,
    int failedSteps,
    boolean requiresHumanFeedback,
    String currentStep,
    String userMessage,
    OutputEvaluationStatus evaluationStatus,  // 新增
    String evaluationLabel                     // 新增
) {
    // 兼容性构造函数（无评估状态）
    public WorkflowSummary(
        WorkflowExecutionStatus status,
        String statusLabel,
        int totalSteps,
        int completedSteps,
        int skippedSteps,
        int failedSteps,
        boolean requiresHumanFeedback,
        String currentStep,
        String userMessage
    ) {
        this(status, statusLabel, totalSteps, completedSteps, skippedSteps, 
             failedSteps, requiresHumanFeedback, currentStep, userMessage, null, null);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl openmanus-spring-ai-alibaba -q`

---

### Task 2: 修改 buildSummary 方法

**Files:**
- Modify: `src/main/java/com/openmanus/saa/service/WorkflowService.java`

- [ ] **Step 1: 添加重载方法，接收 OutputEvaluationResult**

```java
private WorkflowSummary buildSummary(
    String objective,
    List<WorkflowStep> steps,
    WorkflowExecutionStatus status,
    String currentStep,
    String userMessage,
    OutputEvaluationResult outputEvaluation
) {
    int totalSteps = steps.size();
    int completedSteps = (int) steps.stream()
            .filter(step -> step.getStatus() == StepStatus.COMPLETED)
            .count();
    int skippedSteps = (int) steps.stream()
            .filter(step -> step.getStatus() == StepStatus.SKIPPED)
            .count();
    int failedSteps = (int) steps.stream()
            .filter(step -> step.getStatus() == StepStatus.FAILED
                    || step.getStatus() == StepStatus.FAILED_NEEDS_HUMAN_INTERVENTION
                    || step.getStatus() == StepStatus.WAITING_USER_CLARIFICATION)
            .count();

    OutputEvaluationStatus evalStatus = outputEvaluation == null ? null : outputEvaluation.status();
    String evalLabel = evalStatus == null ? null : evaluationStatusLabel(objective, evalStatus);

    return new WorkflowSummary(
            status,
            statusLabel(objective, status, evalStatus),
            totalSteps,
            completedSteps,
            skippedSteps,
            failedSteps,
            status == WorkflowExecutionStatus.NEEDS_HUMAN_INTERVENTION,
            currentStep,
            userMessage,
            evalStatus,
            evalLabel
    );
}
```

- [ ] **Step 2: 更新所有 buildSummary 调用处**

需要更新所有 `buildSummary` 调用，传入 `outputEvaluation` 参数（可能为 null）。

---

### Task 3: 修改 statusLabel 方法

**Files:**
- Modify: `src/main/java/com/openmanus/saa/service/WorkflowService.java`

- [ ] **Step 1: 添加带评估状态的 statusLabel 重载**

```java
private String statusLabel(String objective, WorkflowExecutionStatus status, OutputEvaluationStatus evalStatus) {
    // 评估状态优先
    if (evalStatus == OutputEvaluationStatus.BLOCKER) {
        return ResponseLanguageHelper.choose(objective, "结果不可用", "Output unusable");
    }
    if (evalStatus == OutputEvaluationStatus.MAJOR_ISSUES) {
        return ResponseLanguageHelper.choose(objective, "已完成，需改进", "Completed with issues");
    }
    if (evalStatus == OutputEvaluationStatus.MINOR_ISSUES) {
        return ResponseLanguageHelper.choose(objective, "基本完成", "Completed with minor issues");
    }
    // 默认执行状态
    return statusLabel(objective, status);
}

private String statusLabel(String objective, WorkflowExecutionStatus status) {
    return switch (status) {
        case COMPLETED -> ResponseLanguageHelper.choose(objective, "完全执行成功", "Completed");
        case NEEDS_HUMAN_INTERVENTION -> ResponseLanguageHelper.choose(objective, "需要人为干预", "Needs human intervention");
        case ABORTED -> ResponseLanguageHelper.choose(objective, "已终止", "Aborted");
        case FAILED -> ResponseLanguageHelper.choose(objective, "出现错误", "Failed");
    };
}
```

- [ ] **Step 2: 添加 evaluationStatusLabel 方法**

```java
private String evaluationStatusLabel(String objective, OutputEvaluationStatus status) {
    if (status == null) {
        return null;
    }
    return switch (status) {
        case PASSED -> ResponseLanguageHelper.choose(objective, "通过", "Passed");
        case MINOR_ISSUES -> ResponseLanguageHelper.choose(objective, "可用，仍可小幅优化", "Usable with minor improvements");
        case MAJOR_ISSUES -> ResponseLanguageHelper.choose(objective, "可用，但仍需进一步补充", "Usable but needs major improvements");
        case BLOCKER -> ResponseLanguageHelper.choose(objective, "缺少核心结果", "Core deliverable missing");
        case ASK_USER -> ResponseLanguageHelper.choose(objective, "需要用户确认偏好", "Needs user confirmation");
        case SKIPPED -> ResponseLanguageHelper.choose(objective, "已跳过", "Skipped");
    };
}
```

---

### Task 4: 更新 Markdown 输出格式

**Files:**
- Modify: `src/main/java/com/openmanus/saa/service/WorkflowService.java`

- [ ] **Step 1: 在执行概览中添加评估状态**

```java
// 在 status 之后添加
if (summary.evaluationStatus() != null) {
    markdown.append(chinese ? "- 评估：" : "- Evaluation: ")
            .append(summary.evaluationLabel())
            .append("\n");
}
```

- [ ] **Step 2: 编译并测试**

Run: `mvn compile -pl openmanus-spring-ai-alibaba -q`

---

## 测试验证

执行之前的旅行规划测试，验证：
1. 当 outputEvaluation = MAJOR_ISSUES 时，statusLabel 显示"已完成，需改进"
2. Markdown 输出中显示评估状态
3. 现有不带评估状态的调用仍然正常工作
