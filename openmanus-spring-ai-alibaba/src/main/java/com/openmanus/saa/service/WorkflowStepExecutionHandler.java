package com.openmanus.saa.service;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.openmanus.saa.agent.AgentDefinition;
import com.openmanus.saa.model.StepStatus;
import com.openmanus.saa.model.WorkflowStep;
import com.openmanus.saa.service.agent.SpecialistAgent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class WorkflowStepExecutionHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStepExecutionHandler.class);
    private static final String GRAPH_PLAN_ID_KEY = "planId";
    private static final String GRAPH_WORKFLOW_STEPS_KEY = "workflowSteps";
    private static final String GRAPH_CURRENT_STEP_INDEX_KEY = "currentStepIndex";
    private static final String GRAPH_EXECUTED_STEP_COUNT_KEY = "executedStepCount";
    private static final String GRAPH_LOOP_CONTINUE_KEY = "loopContinue";
    private final WorkflowService service;

    WorkflowStepExecutionHandler(WorkflowService service) {
        this.service = service;
    }

    Map<String, Object> executeSingleStepNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        String planId = state.value(GRAPH_PLAN_ID_KEY, String.class).orElseThrow();
        String objective = state.value("objective", String.class).orElseThrow();
        int actualStepIndex = state.value(GRAPH_CURRENT_STEP_INDEX_KEY, Integer.valueOf(0));
        int executedCount = state.value(GRAPH_EXECUTED_STEP_COUNT_KEY, Integer.valueOf(0));
        List<WorkflowStep> updatedSteps = state.value(GRAPH_WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .map(ArrayList::new)
                .orElseThrow();

        if (actualStepIndex >= updatedSteps.size() || executedCount >= service.properties().getMaxSteps()) {
            return Map.of(
                    GRAPH_WORKFLOW_STEPS_KEY, updatedSteps,
                    GRAPH_CURRENT_STEP_INDEX_KEY, actualStepIndex,
                    GRAPH_EXECUTED_STEP_COUNT_KEY, executedCount,
                    GRAPH_LOOP_CONTINUE_KEY, false
            );
        }

        WorkflowStep step = updatedSteps.get(actualStepIndex);
        if (step.getStatus().isTerminal()) {
            return Map.of(
                    GRAPH_WORKFLOW_STEPS_KEY, updatedSteps,
                    GRAPH_CURRENT_STEP_INDEX_KEY, actualStepIndex + 1,
                    GRAPH_EXECUTED_STEP_COUNT_KEY, executedCount,
                    GRAPH_LOOP_CONTINUE_KEY, actualStepIndex + 1 < updatedSteps.size()
                            && executedCount < service.properties().getMaxSteps()
            );
        }

        log.info("Executing step {}: {}", actualStepIndex + 1, step.getDescription());

        Map<String, Object> executionParameterContext = service.resolveExecutionParameterContext(updatedSteps, actualStepIndex, step);
        WorkflowStep inProgressStep = new WorkflowStep(
                step.getAgent(),
                step.getDescription(),
                step.getRequiredTools(),
                step.getUsedTools(),
                executionParameterContext,
                StepStatus.IN_PROGRESS,
                null,
                LocalDateTime.now(),
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

        WorkflowStep completedStep;
        List<String> displayUsedTools = service.buildDisplayUsedTools(executionResult.usedTools, executionResult.usedToolCalls);
        List<String> resolvedArtifacts = service.resolveStepArtifacts(inProgressStep, executionResult.usedToolCalls, executionResult.artifacts);
        if (executionResult.success) {
            completedStep = inProgressStep.withResult(
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
            return Map.of(
                    GRAPH_WORKFLOW_STEPS_KEY, updatedSteps,
                    GRAPH_CURRENT_STEP_INDEX_KEY, nextIndex,
                    GRAPH_EXECUTED_STEP_COUNT_KEY, nextExecutedCount,
                    GRAPH_LOOP_CONTINUE_KEY, nextIndex < updatedSteps.size()
                            && nextExecutedCount < service.properties().getMaxSteps()
            );
        }

        if (executionResult.needsHumanFeedback) {
            completedStep = inProgressStep.withHumanFeedbackNeeded(
                    executionResult.error,
                    executionResult.usedTools,
                    displayUsedTools,
                    resolvedArtifacts,
                    executionResult.toolOutputs
            );
            updatedSteps.set(actualStepIndex, completedStep);
            service.sessionMemoryService().savePendingFeedback(
                    sessionId,
                    service.createHumanFeedbackRequest(
                            sessionId,
                            objective,
                            planId,
                            actualStepIndex,
                            completedStep,
                            List.copyOf(updatedSteps),
                            executionResult.error
                    )
            );
            log.warn("Step {} requires human intervention. Plan paused.", actualStepIndex + 1);
            return Map.of(
                    GRAPH_WORKFLOW_STEPS_KEY, updatedSteps,
                    GRAPH_CURRENT_STEP_INDEX_KEY, actualStepIndex,
                    GRAPH_EXECUTED_STEP_COUNT_KEY, executedCount,
                    GRAPH_LOOP_CONTINUE_KEY, false
            );
        }

        completedStep = inProgressStep.withFailure(
                executionResult.error,
                executionResult.usedTools,
                displayUsedTools,
                resolvedArtifacts,
                executionResult.toolOutputs,
                executionResult.attempts
        );
        updatedSteps.set(actualStepIndex, completedStep);
        return Map.of(
                GRAPH_WORKFLOW_STEPS_KEY, updatedSteps,
                GRAPH_CURRENT_STEP_INDEX_KEY, actualStepIndex,
                GRAPH_EXECUTED_STEP_COUNT_KEY, executedCount,
                GRAPH_LOOP_CONTINUE_KEY, false
        );
    }
}
