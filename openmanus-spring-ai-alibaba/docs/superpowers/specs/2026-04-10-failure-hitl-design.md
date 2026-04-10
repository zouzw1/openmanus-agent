# 失败恢复耗尽后触发 HITL 设计文档

## 1. 背景

当前工作流中，当步骤执行失败且 `shouldRequestHumanFeedback` 返回 `false` 时（如系统能力问题、POI 服务不可用），工作流直接生成失败响应并结束，用户没有机会介入处理。

用户期望：所有步骤失败在尝试恢复策略后，都进入 Human-in-the-Loop（HITL），让用户决定如何处理。

## 2. 目标

- 当所有恢复策略耗尽后，步骤失败自动触发 HITL
- 用户可选择：跳过步骤、重试、提供信息后重试、终止工作流
- 不引入新的状态类型，复用现有 `needsHumanFeedback` 机制

## 3. 方案：恢复耗尽标记

### 3.1 ExecutionResult 新增字段

**文件**: `WorkflowService.java`

```java
record ExecutionResult(
    boolean success,
    String result,
    List<String> usedTools,
    List<String> usedToolCalls,
    List<String> artifacts,
    List<String> toolOutputs,
    boolean needsHumanFeedback,
    String error,
    int attempt,
    boolean recoveryExhausted   // 新增
) { ... }
```

### 3.2 executeStepWithRetry 标记恢复耗尽

**文件**: `WorkflowService.java`（约 line 384-591）

在以下失败分支中，当 `lazyLoadedStep == null && attempt >= maxAttempts` 时设置 `recoveryExhausted = true`：

1. **FAILED 分支**（line 509-518）：Agent 返回 FAILED 状态
2. **RETRYABLE_ERROR 分支**（line 496-507）：Agent 返回 RETRYABLE_ERROR
3. **skillValidationError**（line 434-446）：未执行必需技能
4. **validationError**（line 454-469）：步骤验证失败
5. **最终 fallback**（line 571, 590）：无结构化结果时的兜底返回

```java
// 示例：FAILED 分支
case FAILED -> {
    error = formatOutcomeError(outcome, outcomeMessage);
    WorkflowStep lazyLoadedStep = maybeAugmentStepWithLazyHelperTools(...);
    if (lazyLoadedStep != null) {
        effectiveStep = lazyLoadedStep;
        error = appendLazyHelperRetryHint(error, effectiveStep);
        continue;
    }
    boolean exhausted = attempt >= maxAttempts;
    return new ExecutionResult(false, null, usedTools, usedToolCalls,
        outcome.artifacts(), toolOutputs, false, error, attempt, exhausted);
}
```

### 3.3 executeStepNode 路由到 HITL

**文件**: `WorkflowLifecycleNodeHandler.java`（约 line 186-234）

修改失败分支条件：

```java
// 需要人工反馈（原有逻辑 OR 恢复耗尽）
if (executionResult.needsHumanFeedback || executionResult.recoveryExhausted()) {
    WorkflowStep completedStep = inProgressStep.withHumanFeedbackNeeded(...);
    updatedSteps.set(actualStepIndex, completedStep);

    HumanFeedbackRequest pendingFeedback = service.createHumanFeedbackRequest(
        sessionId, objective, planId, actualStepIndex,
        completedStep, List.copyOf(updatedSteps), executionResult.error
    );

    service.checkpointService().savePendingFeedback(sessionId, pendingFeedback);
    log.warn("Step {} requires human intervention (recovery exhausted). Plan paused.", actualStepIndex + 1);

    return buildStepResult(updatedSteps, actualStepIndex, executedCount, false);
}
```

### 3.4 HITL 请求建议文案

**文件**: `WorkflowService.java`（约 line 1750-1770）

`createHumanFeedbackRequest` 生成的 `suggestedAction` 包含四个选项：

```
当前步骤执行失败，已尝试以下恢复策略：
- LLM 自我修正重试
- 替代工具/参数重试
- 策略切换重试

请选择：
1. 跳过此步骤继续执行后续步骤
2. 重试此步骤
3. 提供补充信息后重试
4. 终止整个工作流
```

### 3.5 现有流程不变

- `resolveFeedbackNode` 中 `RETRY` 动作：`CURRENT_STEP_INDEX_KEY` 保持不变，回到 `executeStep` 重试
- `resolveFeedbackNode` 中 `SKIP_STEP` 动作：标记步骤为 `SKIPPED`，`CURRENT_STEP_INDEX_KEY + 1`
- `resolveFeedbackNode` 中 `REPLAN` 动作：清除检查点，重新规划
- `resolveFeedbackNode` 中 `ABORT` 动作：路由到 `abortWorkflow` 节点

## 4. 涉及文件

| 文件 | 改动内容 |
|------|---------|
| `WorkflowService.java` | ExecutionResult 新增 `recoveryExhausted`；executeStepWithRetry 标记耗尽 |
| `WorkflowLifecycleNodeHandler.java` | executeStepNode 条件判断中加入 `recoveryExhausted` |

## 5. 不改动的部分

- `shouldRequestHumanFeedback` 逻辑保持不变（仍用于判断用户信息缺失等场景）
- `waitForFeedback` → `resolveFeedback` 图结构不变
- `HumanFeedbackResponse` 的 `ActionType` 枚举不变
- `WorkflowCheckpointService` 不变

## 6. 测试验证

1. 模拟步骤返回 FAILED 且 lazy helper 不可用 → 应触发 HITL
2. 模拟步骤返回 RETRYABLE_ERROR 耗尽重试 → 应触发 HITL
3. 模拟步骤返回 NEEDS_HUMAN_FEEDBACK（原有逻辑）→ 应触发 HITL（回归测试）
4. 模拟步骤成功 → 不应触发 HITL（回归测试）
5. 用户选择 skip → 步骤标记 SKIPPED，继续下一步
6. 用户选择 retry → 步骤重新执行
