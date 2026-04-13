package com.openmanus.saa.service;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.openmanus.saa.agent.AgentDefinition;
import com.openmanus.saa.model.*;
import com.openmanus.saa.model.session.Session;
import com.openmanus.saa.service.WorkflowCheckpointService;
import com.openmanus.saa.service.agent.SpecialistAgent;
import com.openmanus.saa.service.summary.WorkflowSummaryContext;

import java.util.*;

/**
 * 统一工作流生命周期节点处理器。
 *
 * <p>为单一状态图提供所有节点的处理逻辑。
 */
final class WorkflowLifecycleNodeHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkflowLifecycleNodeHandler.class);

    private final WorkflowService service;

    WorkflowLifecycleNodeHandler(WorkflowService service) {
        this.service = service;
    }

    // ========== 节点方法 ==========

    /**
     * 规划节点：生成工作流步骤。
     */
    Map<String, Object> planNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        String objective = state.value("objective", String.class).orElseThrow();
        IntentResolution providedIntentResolution = state.value(WorkflowCheckpointService.INTENT_RESOLUTION_KEY, IntentResolution.class).orElse(null);

        List<WorkflowStep> steps;
        try {
            List<AgentCapabilitySnapshot> agentSnapshots = service.agentCapabilitySnapshotService()
                    .listPlanningVisibleSnapshots(service.properties().isWorkflowUseDataAnalysisAgent());
            steps = service.planningService().createWorkflowPlan(objective, agentSnapshots, providedIntentResolution);
        } catch (PlanningService.PlanValidationException ex) {
            log.warn("Workflow planning failed validation for session {}: {}", sessionId, ex.getMessage());
            return Map.of(
                    WorkflowCheckpointService.WORKFLOW_RESPONSE_KEY,
                    service.createPlanningFailureResponse(objective, ex.getMessage(), ex.getPlannedSteps())
            );
        }

        String planId = "workflow-" + UUID.randomUUID();
        service.planningTools().createPlan(
                planId,
                steps.stream().map(step -> "[" + step.agent() + "] " + step.description()).toList()
        );

        log.info("Created plan {} with {} steps for session {}", planId, steps.size(), sessionId);
        Session session = service.sessionMemoryService().getOrCreate(sessionId);
        ResponseMode responseMode = service.resolveResponseMode(providedIntentResolution, session);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("objective", objective);
        result.put(WorkflowCheckpointService.PLAN_ID_KEY, planId);
        result.put(WorkflowCheckpointService.WORKFLOW_STEPS_KEY, steps);
        result.put(WorkflowCheckpointService.RESPONSE_MODE_KEY, responseMode == null ? ResponseMode.WORKFLOW_SUMMARY : responseMode);
        result.put(WorkflowCheckpointService.INTENT_RESOLUTION_KEY, providedIntentResolution);
        result.put(WorkflowCheckpointService.CURRENT_STEP_INDEX_KEY, 0);
        result.put(WorkflowCheckpointService.EXECUTED_STEP_COUNT_KEY, 0);
        return result;
    }

    /**
     * 计划评估节点：决定是执行步骤还是直接返回。
     */
    Map<String, Object> evaluatePlanNode(OverAllState state) {
        List<WorkflowStep> steps = state.value(WorkflowCheckpointService.WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .orElse(List.of());

        // 如果计划为空，快速路径
        if (steps.isEmpty()) {
            String objective = state.value("objective", String.class).orElseThrow();
            ResponseMode responseMode = state.value(WorkflowCheckpointService.RESPONSE_MODE_KEY, ResponseMode.class)
                    .orElse(ResponseMode.WORKFLOW_SUMMARY);
            return Map.of(
                    WorkflowCheckpointService.WORKFLOW_RESPONSE_KEY,
                    service.createCompletedResponse(objective, steps, responseMode, null, null)
            );
        }

        // Plan 评估
        String objective = state.value("objective", String.class).orElseThrow();
        String sessionId = state.value("sessionId", String.class).orElse(null);
        IntentResolution intentResolution = state.value(WorkflowCheckpointService.INTENT_RESOLUTION_KEY, IntentResolution.class)
                .orElse(null);
        ResponseMode responseMode = state.value(WorkflowCheckpointService.RESPONSE_MODE_KEY, ResponseMode.class)
                .orElse(ResponseMode.WORKFLOW_SUMMARY);
        PlanEvaluationResult evaluation = service.evaluatePlan(objective, steps, intentResolution);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planEvaluationResult", evaluation);

        // 如果需要修订 plan，触发 HITL
        if (evaluation.needsRevision()) {
            HumanFeedbackRequest pendingFeedback = service.createPlanRevisionFeedbackRequest(
                    objective, evaluation.revisionSuggestion(), evaluation.missingElements(), steps
            );
            result.put("pendingFeedback", pendingFeedback);
            result.put(WorkflowCheckpointService.FEEDBACK_WAIT_KEY, true);
            result.put(WorkflowCheckpointService.WORKFLOW_RESPONSE_KEY,
                    service.createPlanRevisionResponse(objective, steps, responseMode, pendingFeedback));
        }

        return result;
    }

    /**
     * 步骤执行节点：执行单个工作流步骤。
     */
    Map<String, Object> executeStepNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        String planId = state.value(WorkflowCheckpointService.PLAN_ID_KEY, String.class).orElseThrow();
        String objective = state.value("objective", String.class).orElseThrow();
        int actualStepIndex = state.value(WorkflowCheckpointService.CURRENT_STEP_INDEX_KEY, Integer.class).orElse(0);
        int executedCount = state.value(WorkflowCheckpointService.EXECUTED_STEP_COUNT_KEY, Integer.class).orElse(0);

        List<WorkflowStep> updatedSteps = state.value(WorkflowCheckpointService.WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .map(ArrayList::new)
                .orElseThrow();

        // 检查是否完成所有步骤
        if (actualStepIndex >= updatedSteps.size() || executedCount >= service.properties().getMaxSteps()) {
            // 工作流正常结束，直接生成响应（因为框架的 interruptsAfter 会中断后续节点）
            ResponseMode responseMode = state.value(WorkflowCheckpointService.RESPONSE_MODE_KEY, ResponseMode.class)
                    .orElse(ResponseMode.WORKFLOW_SUMMARY);
            IntentResolution intentResolution = state.value(WorkflowCheckpointService.INTENT_RESOLUTION_KEY, IntentResolution.class)
                    .orElse(null);

            WorkflowExecutionResponse response = service.finalizeSuccessfulExecution(sessionId, planId, objective, updatedSteps, responseMode, intentResolution);
            Map<String, Object> result = buildStepResult(updatedSteps, actualStepIndex, executedCount, false);
            result.put(WorkflowCheckpointService.WORKFLOW_RESPONSE_KEY, response);
            return result;
        }

        WorkflowStep step = updatedSteps.get(actualStepIndex);

        // 跳过已完成的步骤
        if (step.getStatus().isTerminal()) {
            return buildStepResult(updatedSteps, actualStepIndex + 1, executedCount,
                    actualStepIndex + 1 < updatedSteps.size() && executedCount < service.properties().getMaxSteps());
        }

        log.info("Executing step {}: {}", actualStepIndex + 1, step.getDescription());

        // 执行步骤
        Map<String, Object> executionParameterContext = service.resolveExecutionParameterContext(updatedSteps, actualStepIndex, step);
        WorkflowStep inProgressStep = new WorkflowStep(
                step.getAgent(),
                step.getDescription(),
                step.getRequiredTools(),
                step.getUsedTools(),
                executionParameterContext,
                StepStatus.IN_PROGRESS,
                null,
                java.time.LocalDateTime.now(),
                null,
                null,
                step.getAttemptCount(),
                false
        );

        AgentDefinition agentDefinition = service.resolveAgentDefinition(step.getAgent());
        SpecialistAgent agent = service.selectExecutor(agentDefinition);
        WorkflowService.ExecutionResult executionResult = service.executeStepWithRetry(
                sessionId,
                planId,
                actualStepIndex,
                inProgressStep,
                agentDefinition,
                agent,
                objective,
                updatedSteps,
                service.buildExecutionContext(sessionId, updatedSteps, actualStepIndex)
        );

        List<String> displayUsedTools = service.buildDisplayUsedTools(executionResult.usedTools, executionResult.usedToolCalls);
        List<String> resolvedArtifacts = service.resolveStepArtifacts(inProgressStep, executionResult.usedToolCalls, executionResult.artifacts);

        if (executionResult.success) {
            WorkflowStep completedStep = inProgressStep.withResult(
                    executionResult.result,
                    executionResult.usedTools,
                    displayUsedTools,
                    resolvedArtifacts,
                    executionResult.toolOutputs
            );
            updatedSteps.set(actualStepIndex, completedStep);
            log.info("Step {} completed successfully", actualStepIndex + 1);

            int nextIndex = actualStepIndex + 1;
            int nextExecutedCount = executedCount + 1;
            return buildStepResult(updatedSteps, nextIndex, nextExecutedCount,
                    nextIndex < updatedSteps.size() && nextExecutedCount < service.properties().getMaxSteps());
        }

        // 需要人工反馈（原有逻辑 OR 恢复耗尽）
        if (executionResult.needsHumanFeedback || executionResult.recoveryExhausted) {
            WorkflowStep completedStep = inProgressStep.withHumanFeedbackNeeded(
                    executionResult.error,
                    executionResult.usedTools,
                    displayUsedTools,
                    resolvedArtifacts,
                    executionResult.toolOutputs
            );
            updatedSteps.set(actualStepIndex, completedStep);

            HumanFeedbackRequest pendingFeedback = service.createHumanFeedbackRequest(
                    sessionId,
                    objective,
                    planId,
                    actualStepIndex,
                    completedStep,
                    List.copyOf(updatedSteps),
                    executionResult.error
            );

            // 保存到检查点服务
            service.checkpointService().savePendingFeedback(sessionId, pendingFeedback);

            log.warn("Step {} requires human intervention{}. Plan paused.",
                    actualStepIndex + 1,
                    executionResult.recoveryExhausted ? " (recovery exhausted)" : "");

            // 返回特殊结果，框架会在 executeStep 后中断
            Map<String, Object> result = buildStepResult(updatedSteps, actualStepIndex, executedCount, false);
            result.put(WorkflowCheckpointService.FEEDBACK_WAIT_KEY, true);
            result.put(WorkflowCheckpointService.PENDING_FEEDBACK_KEY, pendingFeedback);
            // 同时设置 HITL 响应，以便 extractResponse 在中断时能找到
            result.put(WorkflowCheckpointService.WORKFLOW_RESPONSE_KEY,
                    service.createPausedResponse(objective, updatedSteps, pendingFeedback));
            return result;
        }

        // 执行失败
        WorkflowStep failedStep = inProgressStep.withFailure(
                executionResult.error,
                executionResult.usedTools,
                displayUsedTools,
                resolvedArtifacts,
                executionResult.toolOutputs,
                executionResult.attempts
        );
        updatedSteps.set(actualStepIndex, failedStep);

        // 工作流失败，直接生成响应
        ResponseMode responseMode = state.value(WorkflowCheckpointService.RESPONSE_MODE_KEY, ResponseMode.class)
                .orElse(ResponseMode.WORKFLOW_SUMMARY);

        WorkflowExecutionResponse response = service.createFailedResponse(objective, updatedSteps, failedStep);
        Map<String, Object> result = buildStepResult(updatedSteps, actualStepIndex, executedCount, false);
        result.put(WorkflowCheckpointService.WORKFLOW_RESPONSE_KEY, response);
        return result;
    }

    /**
     * 汇总输出节点。
     */
    Map<String, Object> finalizeOutputNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        String planId = state.value(WorkflowCheckpointService.PLAN_ID_KEY, String.class).orElseThrow();
        String objective = state.value("objective", String.class).orElseThrow();
        List<WorkflowStep> steps = state.value(WorkflowCheckpointService.WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .orElse(List.of());
        ResponseMode responseMode = state.value(WorkflowCheckpointService.RESPONSE_MODE_KEY, ResponseMode.class)
                .orElse(ResponseMode.WORKFLOW_SUMMARY);
        IntentResolution intentResolution = state.value(WorkflowCheckpointService.INTENT_RESOLUTION_KEY, IntentResolution.class)
                .orElse(null);

        // 检查是否有失败的步骤
        Optional<WorkflowStep> failedStep = steps.stream()
                .filter(step -> step.getStatus() == StepStatus.FAILED || step.getStatus() == StepStatus.FAILED_NEEDS_HUMAN_INTERVENTION)
                .findFirst();

        if (failedStep.isPresent()) {
            // 生成失败响应
            WorkflowExecutionResponse response = service.createFailedResponse(
                    objective, steps, failedStep.get()
            );
            return Map.of(WorkflowCheckpointService.WORKFLOW_RESPONSE_KEY, response);
        }

        // 检查是否有需要人工反馈的步骤
        Optional<WorkflowStep> needsFeedbackStep = steps.stream()
                .filter(step -> step.getStatus() == StepStatus.WAITING_USER_CLARIFICATION)
                .findFirst();

        if (needsFeedbackStep.isPresent()) {
            // 生成需要反馈的响应
            WorkflowExecutionResponse response = service.createFailedResponse(
                    objective, steps, needsFeedbackStep.get()
            );
            return Map.of(WorkflowCheckpointService.WORKFLOW_RESPONSE_KEY, response);
        }

        // 正常完成
        return Map.of(
                WorkflowCheckpointService.WORKFLOW_RESPONSE_KEY,
                service.finalizeSuccessfulExecution(sessionId, planId, objective, steps, responseMode, intentResolution)
        );
    }

    /**
     * 评估输出节点。
     */
    Map<String, Object> evaluateOutputNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        String objective = state.value("objective", String.class).orElseThrow();
        ResponseMode responseMode = state.value(WorkflowCheckpointService.RESPONSE_MODE_KEY, ResponseMode.class)
                .orElse(ResponseMode.WORKFLOW_SUMMARY);
        int retryCount = state.value("outputEvaluationRetryCount", Integer.class).orElse(0);

        List<WorkflowStep> currentSteps = state.value(WorkflowCheckpointService.WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .orElse(List.of());

        // 检查是否应该评估
        if (!service.shouldEvaluateOutput(responseMode)) {
            OutputEvaluationResult result = new OutputEvaluationResult(
                    OutputEvaluationStatus.SKIPPED, "", List.of(), null, 0,
                    WorkflowService.DEFAULT_OUTPUT_EVALUATION_MAX_RETRIES
            );
            return Map.of(
                    WorkflowCheckpointService.WORKFLOW_RESPONSE_KEY,
                    service.createCompletedResponse(objective, currentSteps, responseMode, result, null)
            );
        }

        WorkflowService.OutputEvaluationDecision decision = service.evaluateOutput(objective, currentSteps, responseMode);
        service.sessionMemoryService().getOrCreate(sessionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("outputEvaluationDecision", decision);
        result.put("outputEvaluationResult", decision.toResult(retryCount, WorkflowService.DEFAULT_OUTPUT_EVALUATION_MAX_RETRIES, false));
        result.put("outputEvaluationRetryCount", retryCount);
        return result;
    }

    /**
     * 重试步骤节点。
     */
    Map<String, Object> retryStepNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        String planId = state.value(WorkflowCheckpointService.PLAN_ID_KEY, String.class).orElseThrow();
        String objective = state.value("objective", String.class).orElseThrow();
        ResponseMode responseMode = state.value(WorkflowCheckpointService.RESPONSE_MODE_KEY, ResponseMode.class)
                .orElse(ResponseMode.WORKFLOW_SUMMARY);
        WorkflowService.OutputEvaluationDecision decision = state.value("outputEvaluationDecision", WorkflowService.OutputEvaluationDecision.class)
                .orElse(null);
        int retryCount = state.value("outputEvaluationRetryCount", Integer.class).orElse(0) + 1;

        List<WorkflowStep> currentSteps = state.value(WorkflowCheckpointService.WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .orElse(List.of());

        Session session = service.sessionMemoryService().getOrCreate(sessionId);
        Session updatedSession = session.addExecutionLog("Output evaluation requested automatic retry #" + retryCount);
        if (decision != null && decision.revisionPrompt() != null && !decision.revisionPrompt().isBlank()) {
            updatedSession = updatedSession.addSystemMessage("[OUTPUT_EVALUATION_RETRY] " + decision.revisionPrompt());
        }
        service.sessionMemoryService().saveSession(updatedSession);

        int retryStartIndex = service.clampRetryStartIndex(currentSteps, decision != null ? decision.retryStartIndex() : 0);
        List<WorkflowStep> retriedSteps = service.prepareStepsForOutputRetry(currentSteps, decision, retryCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(WorkflowCheckpointService.WORKFLOW_STEPS_KEY, retriedSteps);
        result.put(WorkflowCheckpointService.RESPONSE_MODE_KEY, responseMode);
        result.put("outputEvaluationRetryCount", retryCount);
        result.put(WorkflowCheckpointService.CURRENT_STEP_INDEX_KEY, retryStartIndex);
        return result;
    }

    /**
     * 反馈处理节点（RESUME 入口）。
     */
    Map<String, Object> resolveFeedbackNode(OverAllState state) {
        HumanFeedbackResponse feedback = state.value(WorkflowCheckpointService.FEEDBACK_RESPONSE_KEY, Object.class)
                .map(service::coerceHumanFeedbackResponse)
                .orElseThrow();
        HumanFeedbackRequest pendingFeedback = state.value(WorkflowCheckpointService.PENDING_FEEDBACK_KEY, Object.class)
                .map(service::coerceHumanFeedbackRequest)
                .orElseThrow();

        // sessionId 优先从 state 取，fallback 从 pendingFeedback 取（resume 后 state 中可能没有 sessionId）
        String sessionId = state.value("sessionId", String.class)
                .orElse(pendingFeedback.getSessionId());

        log.info("Resolving feedback for session {}, action={}", sessionId, feedback.getAction());

        // 根据反馈动作决定下一步
        if (feedback.isReplanRequired()) {
            String updatedObjective = feedback.getUpdatedObjective();
            if (updatedObjective == null || updatedObjective.isBlank()) {
                updatedObjective = pendingFeedback.getObjective();
            }
            log.info("Replanning workflow for session {}. New objective='{}'", sessionId, updatedObjective);

            // 清除旧的检查点
            service.checkpointService().release(sessionId);

            return Map.of("objective", updatedObjective);
        }

        // 处理继续执行
        if (feedback.getAction() == HumanFeedbackResponse.ActionType.SKIP_STEP) {
            List<WorkflowStep> steps = service.copyWorkflowSteps(service.resolveWorkflowSteps(pendingFeedback));
            if (pendingFeedback.getStepIndex() < steps.size()) {
                WorkflowStep skippedStep = steps.get(pendingFeedback.getStepIndex());
                steps.set(pendingFeedback.getStepIndex(), new WorkflowStep(
                        skippedStep.getAgent(),
                        skippedStep.getDescription(),
                        skippedStep.getRequiredTools(),
                        skippedStep.getUsedTools(),
                        skippedStep.getParameterContext(),
                        StepStatus.SKIPPED,
                        "Skipped after human feedback.",
                        skippedStep.getStartTime(),
                        java.time.LocalDateTime.now(),
                        null,
                        skippedStep.getAttemptCount(),
                        false
                ));
            }
            service.checkpointService().clearPendingFeedback(sessionId);
            return Map.of(
                    WorkflowCheckpointService.WORKFLOW_STEPS_KEY, steps,
                    WorkflowCheckpointService.CURRENT_STEP_INDEX_KEY, pendingFeedback.getStepIndex() + 1
            );
        }

        // RETRY / PROVIDE_INFO / MODIFY_AND_RETRY - 继续执行
        service.checkpointService().clearPendingFeedback(sessionId);
        return Map.of(
                WorkflowCheckpointService.CURRENT_STEP_INDEX_KEY, pendingFeedback.getStepIndex()
        );
    }

    /**
     * 终止工作流节点。
     */
    Map<String, Object> abortWorkflowNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        HumanFeedbackRequest pendingFeedback = state.value(WorkflowCheckpointService.PENDING_FEEDBACK_KEY, Object.class)
                .map(service::coerceHumanFeedbackRequest)
                .orElseThrow();

        List<WorkflowStep> allSteps = service.resolveWorkflowSteps(pendingFeedback);
        String baseMessage = String.format(
                "Workflow aborted after human feedback.\n\nPlan ID: %s\nCurrent step: %s\nReason: %s",
                pendingFeedback.getPlanId(),
                pendingFeedback.getFailedStep().getDescription(),
                pendingFeedback.getErrorMessage()
        );

        String content = service.formatSummaryMessage(new WorkflowSummaryContext(
                pendingFeedback.getObjective(),
                WorkflowExecutionStatus.ABORTED,
                pendingFeedback.getFailedStep().getDescription(),
                baseMessage,
                pendingFeedback.getFailedStep(),
                null,
                allSteps,
                service.collectResponseArtifacts(allSteps),
                service.toExecutionLog(allSteps)
        ));

        Session session = service.sessionMemoryService().getOrCreate(sessionId);
        service.sessionMemoryService().saveSession(session.addAssistantMessage(content));

        // 释放检查点
        service.checkpointService().release(sessionId);

        return Map.of(
                WorkflowCheckpointService.WORKFLOW_RESPONSE_KEY,
                new WorkflowExecutionResponse(
                        pendingFeedback.getObjective(),
                        allSteps,
                        service.collectResponseArtifacts(allSteps),
                        List.of(content),  // executionLog: 终止说明
                        service.buildSummary(pendingFeedback.getObjective(), allSteps, WorkflowExecutionStatus.ABORTED,
                                pendingFeedback.getFailedStep().getDescription(), null),
                        null,
                        null
                )
        );
    }

    /**
     * 返回响应节点。
     */
    Map<String, Object> returnResponseNode(OverAllState state) {
        return state.value(WorkflowCheckpointService.WORKFLOW_RESPONSE_KEY, Object.class)
                .map(r -> Map.of(WorkflowCheckpointService.WORKFLOW_RESPONSE_KEY, r))
                .orElse(Map.of());
    }

    // ========== 边路由方法 ==========

    String selectPlanTransition(OverAllState state) {
        // 如果有响应，说明规划失败
        return state.value(WorkflowCheckpointService.WORKFLOW_RESPONSE_KEY, Object.class).isPresent() ? "return" : "evaluate";
    }

    String selectEvaluatePlanTransition(OverAllState state) {
        List<WorkflowStep> steps = state.value(WorkflowCheckpointService.WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .orElse(List.of());

        if (steps.isEmpty()) {
            return "finalize";
        }

        // 检查 Plan 评估结果
        PlanEvaluationResult evaluation = state.value("planEvaluationResult", PlanEvaluationResult.class)
                .orElse(PlanEvaluationResult.ok());

        if (evaluation.needsRevision()) {
            // 触发 HITL，让用户确认是否修订 plan
            return "planRevision";
        }

        return "execute";
    }

    String selectExecuteStepTransition(OverAllState state) {
        // 如果需要人工反馈，路由到 waitForFeedback 节点（框架会在此中断）
        Boolean feedbackWait = state.value(WorkflowCheckpointService.FEEDBACK_WAIT_KEY, Boolean.class).orElse(false);
        if (Boolean.TRUE.equals(feedbackWait)) {
            return "interrupt";
        }

        Boolean loopContinue = state.value(WorkflowCheckpointService.LOOP_CONTINUE_KEY, Boolean.class).orElse(false);
        return loopContinue ? "loop" : "finalize";
    }

    String selectOutputEvaluationTransition(OverAllState state) {
        String objective = state.value("objective", String.class).orElseThrow();
        List<WorkflowStep> currentSteps = state.value(WorkflowCheckpointService.WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .orElse(List.of());
        WorkflowService.OutputEvaluationDecision decision = state.value("outputEvaluationDecision", WorkflowService.OutputEvaluationDecision.class)
                .orElse(null);
        int retryCount = state.value("outputEvaluationRetryCount", Integer.class).orElse(0);

        if (decision == null) {
            return "complete";
        }

        if (decision.status() == OutputEvaluationStatus.PASSED || decision.status() == OutputEvaluationStatus.MINOR_ISSUES) {
            return "complete";
        }
        if (service.shouldRetryAfterEvaluation(decision, retryCount)) {
            return "retry";
        }
        if (decision.status() == OutputEvaluationStatus.MAJOR_ISSUES
                || (decision.status() == OutputEvaluationStatus.ASK_USER && service.hasCoreDeliverable(objective, currentSteps))) {
            return "complete";
        }
        return "complete";
    }

    String selectFeedbackTransition(OverAllState state) {
        String nextNode = state.value(WorkflowCheckpointService.NEXT_NODE_KEY, String.class).orElse("execute");
        return nextNode;
    }

    // ========== 辅助方法 ==========

    private Map<String, Object> buildStepResult(List<WorkflowStep> steps, int currentIndex, int executedCount, boolean loopContinue) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(WorkflowCheckpointService.WORKFLOW_STEPS_KEY, steps);
        result.put(WorkflowCheckpointService.CURRENT_STEP_INDEX_KEY, currentIndex);
        result.put(WorkflowCheckpointService.EXECUTED_STEP_COUNT_KEY, executedCount);
        result.put(WorkflowCheckpointService.LOOP_CONTINUE_KEY, loopContinue);
        return result;
    }
}
