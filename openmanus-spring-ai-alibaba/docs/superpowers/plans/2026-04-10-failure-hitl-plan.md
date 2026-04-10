# 失败恢复耗尽后触发 HITL 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 当所有恢复策略耗尽后，步骤失败自动触发 Human-in-the-Loop，让用户决定跳过、重试、提供信息或终止工作流。

**Architecture:** 在现有 `ExecutionResult` 中新增 `recoveryExhausted` 标记，由 `executeStepWithRetry` 在所有恢复路径耗尽时设置。`executeStepNode` 检测此标记后走 HITL 分支，复用现有 `waitForFeedback` → `resolveFeedback` 图结构。

**Tech Stack:** Java 17+, Spring Boot, spring-ai-alibaba-graph (StateGraph)

---

## 文件变更总览

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `WorkflowService.java` | 修改 | ExecutionResult 新增字段；executeStepWithRetry 标记耗尽；generateSuggestedAction 增强文案 |
| `WorkflowLifecycleNodeHandler.java` | 修改 | executeStepNode 加入 recoveryExhausted 条件 |
| `WorkflowServiceRegressionTest.java` | 修改 | 新增恢复耗尽触发 HITL 的回归测试 |

---

### Task 1: ExecutionResult 新增 recoveryExhausted 字段

**文件:** `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/WorkflowService.java`

- [ ] **Step 1: 在 ExecutionResult 类中添加字段和构造函数参数**

在 `WorkflowService.java` line 3709 的 `ExecutionResult` 类中：

```java
static class ExecutionResult {
    final boolean success;
    final String result;
    final List<String> usedTools;
    final List<String> usedToolCalls;
    final List<String> artifacts;
    final List<String> toolOutputs;
    final boolean needsHumanFeedback;
    final String error;
    final int attempts;
    final boolean recoveryExhausted;   // 新增

    ExecutionResult(
            boolean success,
            String result,
            List<String> usedTools,
            List<String> usedToolCalls,
            List<String> artifacts,
            List<String> toolOutputs,
            boolean needsHumanFeedback,
            String error,
            int attempts,
            boolean recoveryExhausted   // 新增
    ) {
        this.success = success;
        this.result = result;
        this.usedTools = usedTools == null ? List.of() : List.copyOf(usedTools);
        this.usedToolCalls = usedToolCalls == null ? List.of() : List.copyOf(usedToolCalls);
        this.artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        this.toolOutputs = toolOutputs == null ? List.of() : List.copyOf(toolOutputs);
        this.needsHumanFeedback = needsHumanFeedback;
        this.error = error;
        this.attempts = attempts;
        this.recoveryExhausted = recoveryExhausted;
    }
}
```

- [ ] **Step 2: 更新所有现有构造函数调用**

需要更新所有 `new ExecutionResult(...)` 调用，添加第 10 个参数 `recoveryExhausted`。按位置列出：

**line 417** — skillLoadResult.error 路径（循环内，skill 加载失败直接返回，不走恢复流程）：
```java
return new ExecutionResult(false, null, skillLoadResult.usedTools, skillLoadResult.usedToolCalls, List.of(), List.of(), false, skillLoadResult.error, attempt, false);
```

**line 444** — skillValidationError 耗尽（lazyLoadedStep==null && attempt>=maxAttempts）：
```java
return new ExecutionResult(false, null, usedTools, usedToolCalls, List.of(), toolOutputs, false, skillValidationError, attempt, true);
```

**line 456** — SUCCESS（成功路径）：
```java
return new ExecutionResult(true, outcomeMessage, usedTools, usedToolCalls, outcome.artifacts(), toolOutputs, false, null, attempt, false);
```

**line 467** — validationError 耗尽（lazyLoadedStep==null && attempt>=maxAttempts）：
```java
return new ExecutionResult(false, null, usedTools, usedToolCalls, outcome.artifacts(), toolOutputs, false, validationError, attempt, true);
```

**line 484-494** — NEEDS_HUMAN_FEEDBACK（已有 needsHumanFeedback 判断）：
```java
return new ExecutionResult(false, null, usedTools, usedToolCalls, outcome.artifacts(), toolOutputs, needsHumanFeedback, error, attempt, false);
```

**line 505** — RETRYABLE_ERROR 耗尽（lazyLoadedStep==null && attempt>=maxAttempts）：
```java
return new ExecutionResult(false, null, usedTools, usedToolCalls, outcome.artifacts(), toolOutputs, false, error, attempt, true);
```

**line 517** — FAILED（lazyLoadedStep==null，无 attempt>=maxAttempts 检查）：
```java
return new ExecutionResult(false, null, usedTools, usedToolCalls, outcome.artifacts(), toolOutputs, false, error, attempt, true);
```

**line 526** — SUCCESS（无结构化结果但 ParameterMissingDetector.SUCCESS）：
```java
return new ExecutionResult(true, result, usedTools, usedToolCalls, List.of(), toolOutputs, false, null, attempt, false);
```

**line 537** — validationError 耗尽（无结构化结果路径）：
```java
return new ExecutionResult(false, null, usedTools, usedToolCalls, List.of(), toolOutputs, false, validationError, attempt, true);
```

**line 561** — MISSING_PARAMETERS with needsHumanFeedback：
```java
return new ExecutionResult(false, null, usedTools, usedToolCalls, List.of(), toolOutputs, needsHumanFeedback, finalError, attempt, false);
```

**line 571** — 无结构化结果的 catch-all：
```java
return new ExecutionResult(false, null, usedTools, usedToolCalls, List.of(), toolOutputs, false, error, attempt, true);
```

**line 585** — 异常 catch 块（lazyLoadedStep==null && attempt>=maxAttempts）：
```java
return new ExecutionResult(false, null, usedTools, usedToolCalls, List.of(), toolOutputs, false, error, attempt, true);
```

**line 590** — while 循环最终 fallback：
```java
return new ExecutionResult(false, null, usedTools, usedToolCalls, List.of(), toolOutputs, false, error, attempt, true);
```

**line 610-620** — tryExecuteDirectWorkspaceWrite 成功：
```java
return new ExecutionResult(true, "Wrote file: " + ..., List.of("writeWorkspaceFile"), List.of(), List.of(target.toString()), List.of(...), false, null, attempt, false);
```

**line 622-632** — tryExecuteDirectWorkspaceWrite 失败：
```java
return new ExecutionResult(false, null, List.of("writeWorkspaceFile"), List.of(), List.of(), List.of(), false, "Failed to write workspace file directly: " + ex.getMessage(), attempt, false);
```

- [ ] **Step 3: 编译验证**

```bash
cd openmanus-spring-ai-alibaba && mvn compile -pl . -q
```

---

### Task 2: executeStepNode 加入 recoveryExhausted 判断

**文件:** `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/WorkflowLifecycleNodeHandler.java`

- [ ] **Step 1: 修改 executeStepNode 的失败分支条件**

在 `WorkflowLifecycleNodeHandler.java` line 186-214，将：

```java
// 需要人工反馈
if (executionResult.needsHumanFeedback) {
```

改为：

```java
// 需要人工反馈（原有逻辑 OR 恢复耗尽）
if (executionResult.needsHumanFeedback || executionResult.recoveryExhausted) {
```

并更新日志消息：

```java
log.warn("Step {} requires human intervention{}. Plan paused.", 
    actualStepIndex + 1,
    executionResult.recoveryExhausted ? " (recovery exhausted)" : "");
```

- [ ] **Step 2: 编译验证**

```bash
cd openmanus-spring-ai-alibaba && mvn compile -pl . -q
```

---

### Task 3: 增强 HITL 建议文案

**文件:** `openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/WorkflowService.java`

- [ ] **Step 1: 修改 generateSuggestedAction 方法**

在 `generateSuggestedAction`（line 1858）的默认分支前，添加恢复耗尽的专用提示：

```java
private String generateSuggestedAction(String objective, String errorMessage) {
    if (errorMessage == null || errorMessage.isBlank()) {
        return ResponseLanguageHelper.choose(
                objective,
                "请补充必要上下文，或选择重试、跳过、终止当前步骤。",
                "Provide the missing context or choose whether to retry or skip the step."
        );
    }

    String normalized = errorMessage.toLowerCase();

    // 恢复耗尽场景的专用提示
    if (normalized.contains("recovery exhausted") || normalized.contains("all recovery strategies")) {
        return ResponseLanguageHelper.choose(
                objective,
                "当前步骤执行失败，已尝试所有恢复策略。\n\n请选择：\n1. 跳过此步骤继续执行后续步骤\n2. 重试此步骤\n3. 提供补充信息后重试\n4. 终止整个工作流",
                "All recovery strategies have been exhausted for this step.\n\nPlease choose:\n1. Skip this step and continue\n2. Retry this step\n3. Provide additional info and retry\n4. Abort the entire workflow"
        );
    }

    // ... 保留现有分支（forecast, missing parameter, unable to retrieve 等）
    // ...
}
```

**注意**: 这个改动是可选的增强。核心功能不依赖此改动——`createHumanFeedbackRequest` 已经会根据 `errorMessage` 生成合理的建议。如果错误消息中包含 "recovery exhausted" 字样，用户会看到更具体的指导。

- [ ] **Step 2: 在 executeStepWithRetry 的耗尽路径中注入标识**

在所有 `recoveryExhausted=true` 的返回点，将 error 消息前缀加上标识，以便 `generateSuggestedAction` 能识别：

```java
// 示例：FAILED 分支
boolean exhausted = attempt >= maxAttempts;
String enrichedError = exhausted
    ? "[recovery exhausted] " + error
    : error;
return new ExecutionResult(false, null, usedTools, usedToolCalls,
    outcome.artifacts(), toolOutputs, false, enrichedError, attempt, exhausted);
```

对所有 6 个 `recoveryExhausted=true` 的返回点应用相同模式。

- [ ] **Step 3: 编译验证**

```bash
cd openmanus-spring-ai-alibaba && mvn compile -pl . -q
```

---

### Task 4: 新增回归测试

**文件:** `openmanus-spring-ai-alibaba/src/test/java/com/openmanus/saa/service/WorkflowServiceRegressionTest.java`

- [ ] **Step 1: 添加测试用例**

在 `WorkflowServiceRegressionTest` 类末尾添加测试：

```java
@Test
void executeStepWithRetrySetsRecoveryExhaustedOnFinalFailure() throws Exception {
    // 这个测试验证当所有恢复策略耗尽时，ExecutionResult.recoveryExhausted == true
    // 由于 executeStepWithRetry 是包级私有方法，通过 WorkflowLifecycleNodeHandler 间接测试
    
    WorkflowService service = mock(WorkflowService.class);
    WorkflowLifecycleNodeHandler handler = new WorkflowLifecycleNodeHandler(service);
    
    // 模拟一个恢复耗尽的 ExecutionResult
    WorkflowService.ExecutionResult exhaustedResult = new WorkflowService.ExecutionResult(
        false,                    // success
        null,                     // result
        List.of("searchTool"),    // usedTools
        List.of(),                // usedToolCalls
        List.of(),                // artifacts
        List.of(),                // toolOutputs
        false,                    // needsHumanFeedback
        "Service unavailable",   // error
        2,                        // attempts (maxAttempts)
        true                      // recoveryExhausted
    );
    
    assertThat(exhaustedResult.recoveryExhausted).isTrue();
    assertThat(exhaustedResult.needsHumanFeedback).isFalse();
    assertThat(exhaustedResult.success).isFalse();
}

@Test
void executeStepWithRetryDoesNotSetRecoveryExhaustedOnSuccess() {
    WorkflowService.ExecutionResult successResult = new WorkflowService.ExecutionResult(
        true,                     // success
        "Step completed",         // result
        List.of(),                // usedTools
        List.of(),                // usedToolCalls
        List.of(),                // artifacts
        List.of(),                // toolOutputs
        false,                    // needsHumanFeedback
        null,                     // error
        1,                        // attempts
        false                     // recoveryExhausted
    );
    
    assertThat(successResult.recoveryExhausted).isFalse();
    assertThat(successResult.success).isTrue();
}

@Test
void executeStepWithRetryDoesNotSetRecoveryExhaustedOnAlreadyNeedsHumanFeedback() {
    // NEEDS_HUMAN_FEEDBACK 状态已有 needsHumanFeedback=true，不应额外设置 recoveryExhausted
    WorkflowService.ExecutionResult hitlResult = new WorkflowService.ExecutionResult(
        false,                    // success
        null,                     // result
        List.of(),                // usedTools
        List.of(),                // usedToolCalls
        List.of(),                // artifacts
        List.of(),                // toolOutputs
        true,                     // needsHumanFeedback
        "Missing user input",     // error
        1,                        // attempts
        false                     // recoveryExhausted
    );
    
    assertThat(hitlResult.needsHumanFeedback).isTrue();
    assertThat(hitlResult.recoveryExhausted).isFalse();
}
```

- [ ] **Step 2: 运行测试**

```bash
cd openmanus-spring-ai-alibaba && mvn test -pl . -Dtest=WorkflowServiceRegressionTest -q
```

- [ ] **Step 3: 提交**

```bash
git add -A
git commit -m "feat: trigger HITL when step recovery is exhausted

- Add recoveryExhausted field to ExecutionResult
- Mark recoveryExhausted=true in all failure return paths
- Route to HITL in executeStepNode when recoveryExhausted
- Enhance suggestedAction for recovery-exhausted scenarios
- Add regression tests for recoveryExhausted field

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## 验证清单

- [ ] `mvn compile` 通过（所有 ExecutionResult 构造函数调用已更新）
- [ ] 现有测试通过（回归测试无破坏）
- [ ] 新增测试通过（recoveryExhausted 字段行为正确）
- [ ] 人工验证：模拟步骤返回 FAILED 且 lazy helper 不可用 → 应触发 HITL
- [ ] 人工验证：模拟步骤成功 → 不应触发 HITL
