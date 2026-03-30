package com.openmanus.saa.service;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.openmanus.saa.model.AgentCapabilitySnapshot;
import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.HumanFeedbackResponse;
import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.OutputEvaluationResult;
import com.openmanus.saa.model.OutputEvaluationStatus;
import com.openmanus.saa.model.ResponseMode;
import com.openmanus.saa.model.WorkflowExecutionResponse;
import com.openmanus.saa.model.WorkflowStep;
import com.openmanus.saa.model.session.SessionState;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class WorkflowLifecycleNodeHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowLifecycleNodeHandler.class);
    private static final String GRAPH_RESPONSE_KEY = "workflowResponse";
    private static final String GRAPH_PENDING_FEEDBACK_KEY = "pendingFeedback";
    private static final String GRAPH_INTENT_RESOLUTION_KEY = "intentResolution";
    private static final String GRAPH_PLAN_ID_KEY = "planId";
    private static final String GRAPH_WORKFLOW_STEPS_KEY = "workflowSteps";
    private static final String GRAPH_FAILED_STEP_KEY = "failedStep";
    private static final String GRAPH_RESPONSE_MODE_KEY = "responseMode";
    private static final String GRAPH_START_INDEX_KEY = "startIndex";
    private static final String GRAPH_OUTPUT_EVALUATION_RESULT_KEY = "outputEvaluationResult";
    private static final String GRAPH_OUTPUT_EVALUATION_DECISION_KEY = "outputEvaluationDecision";
    private static final String GRAPH_OUTPUT_EVALUATION_RETRY_COUNT_KEY = "outputEvaluationRetryCount";
    private final WorkflowService service;

    WorkflowLifecycleNodeHandler(WorkflowService service) {
        this.service = service;
    }

    Map<String, Object> checkPendingFeedbackNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        String objective = state.value("objective", String.class).orElseThrow();
        SessionState session = service.sessionMemoryService().getOrCreate(sessionId);
        String resolvedSessionId = session.getSessionId();
        Optional<HumanFeedbackRequest> pendingFeedback = service.sessionMemoryService().getPendingFeedback(resolvedSessionId);
        if (pendingFeedback.isPresent()) {
            log.info("Session {} still waiting for human feedback", resolvedSessionId);
            return Map.of(
                    "sessionId", resolvedSessionId,
                    "objective", objective,
                    GRAPH_PENDING_FEEDBACK_KEY, pendingFeedback.get()
            );
        }
        return Map.of(
                "sessionId", resolvedSessionId,
                "objective", objective
        );
    }

    Map<String, Object> returnPausedNode(OverAllState state) {
        HumanFeedbackRequest pendingFeedback = state.value(GRAPH_PENDING_FEEDBACK_KEY, Object.class)
                .map(service::coerceHumanFeedbackRequest)
                .orElseThrow();
        WorkflowExecutionResponse response = service.createPausedResponse(
                pendingFeedback.getObjective(),
                service.resolveWorkflowSteps(pendingFeedback),
                pendingFeedback
        );
        return Map.of(GRAPH_RESPONSE_KEY, response);
    }

    Map<String, Object> resolveAndExecuteNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        String objective = state.value("objective", String.class).orElseThrow();
        SessionState session = service.sessionMemoryService().getOrCreate(sessionId);
        IntentResolution providedIntentResolution = state.value(GRAPH_INTENT_RESOLUTION_KEY, IntentResolution.class).orElse(null);
        IntentResolution intentResolution = providedIntentResolution == null
                ? service.intentResolutionService().resolve(objective, session)
                : providedIntentResolution;
        ResponseMode responseMode = service.resolveResponseMode(intentResolution, session);
        if (responseMode != null) {
            session.setLatestResponseMode(responseMode);
            session.addExecutionLog("Response mode resolved: " + responseMode);
        }
        session.addExecutionLog("Intent resolved: " + intentResolution.intentId() + " -> " + intentResolution.routeMode());
        session.addMessage("user", objective);
        WorkflowExecutionResponse response = service.executeNewPlan(sessionId, objective, intentResolution);
        return Map.of(GRAPH_RESPONSE_KEY, response);
    }

    Map<String, Object> loadPendingFeedbackNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        HumanFeedbackResponse feedback = state.value("feedback", HumanFeedbackResponse.class).orElseThrow();
        HumanFeedbackRequest pendingFeedback = service.sessionMemoryService().getPendingFeedback(sessionId)
                .orElseThrow(() -> new IllegalStateException("No pending workflow feedback found for session: " + sessionId));
        service.sessionMemoryService().processFeedback(sessionId, feedback);
        return Map.of(
                "sessionId", sessionId,
                "feedback", feedback,
                GRAPH_PENDING_FEEDBACK_KEY, pendingFeedback
        );
    }

    String selectFeedbackTransition(OverAllState state) {
        HumanFeedbackResponse feedback = state.value("feedback", HumanFeedbackResponse.class).orElseThrow();
        if (feedback.isReplanRequired()) {
            return "replan";
        }
        return switch (feedback.getAction()) {
            case ABORT_PLAN -> "abort";
            case SKIP_STEP -> "skip";
            case RETRY, PROVIDE_INFO, MODIFY_AND_RETRY -> "continue";
        };
    }

    Map<String, Object> replanFromFeedbackNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        HumanFeedbackResponse feedback = state.value("feedback", HumanFeedbackResponse.class).orElseThrow();
        HumanFeedbackRequest pendingFeedback = state.value(GRAPH_PENDING_FEEDBACK_KEY, Object.class)
                .map(service::coerceHumanFeedbackRequest)
                .orElseThrow();
        String updatedObjective = feedback.getUpdatedObjective();
        if (updatedObjective == null || updatedObjective.isBlank()) {
            updatedObjective = pendingFeedback.getObjective();
        }
        log.info(
                "Replanning workflow for session {} after human feedback. Old objective='{}', new objective='{}'",
                sessionId,
                pendingFeedback.getObjective(),
                updatedObjective
        );
        return Map.of(GRAPH_RESPONSE_KEY, service.executeNewPlan(sessionId, updatedObjective, null));
    }

    Map<String, Object> abortFromFeedbackNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        HumanFeedbackRequest pendingFeedback = state.value(GRAPH_PENDING_FEEDBACK_KEY, Object.class)
                .map(service::coerceHumanFeedbackRequest)
                .orElseThrow();
        return Map.of(GRAPH_RESPONSE_KEY, service.abortPlan(sessionId, pendingFeedback));
    }

    Map<String, Object> skipFromFeedbackNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        HumanFeedbackRequest pendingFeedback = state.value(GRAPH_PENDING_FEEDBACK_KEY, Object.class)
                .map(service::coerceHumanFeedbackRequest)
                .orElseThrow();
        return Map.of(GRAPH_RESPONSE_KEY, service.skipCurrentStep(sessionId, pendingFeedback));
    }

    Map<String, Object> continueFromFeedbackNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        HumanFeedbackRequest pendingFeedback = state.value(GRAPH_PENDING_FEEDBACK_KEY, Object.class)
                .map(service::coerceHumanFeedbackRequest)
                .orElseThrow();
        return Map.of(
                GRAPH_RESPONSE_KEY,
                service.continueExecution(
                        sessionId,
                        pendingFeedback.getObjective(),
                        pendingFeedback.getPlanId(),
                        service.copyWorkflowSteps(service.resolveWorkflowSteps(pendingFeedback)),
                        pendingFeedback.getStepIndex()
                )
        );
    }

    Map<String, Object> createPlanContextNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        String objective = state.value("objective", String.class).orElseThrow();
        IntentResolution intentResolution = state.value(GRAPH_INTENT_RESOLUTION_KEY, IntentResolution.class).orElse(null);
        List<WorkflowStep> steps;
        List<AgentCapabilitySnapshot> agentSnapshots = service.agentCapabilitySnapshotService()
                .listPlanningVisibleSnapshots(service.properties().isWorkflowUseDataAnalysisAgent());
        try {
            steps = service.planningService().createWorkflowPlan(objective, agentSnapshots, intentResolution);
        } catch (PlanningService.PlanValidationException ex) {
            log.warn("Workflow planning failed validation for session {}: {}", sessionId, ex.getMessage());
            return Map.of(GRAPH_RESPONSE_KEY, service.createPlanningFailureResponse(objective, ex.getMessage(), ex.getPlannedSteps()));
        }

        String planId = "workflow-" + UUID.randomUUID();
        service.planningTools().createPlan(
                planId,
                steps.stream().map(step -> "[" + step.agent() + "] " + step.description()).toList()
        );

        log.info("Created plan {} with {} steps for session {}", planId, steps.size(), sessionId);
        SessionState session = service.sessionMemoryService().getOrCreate(sessionId);
        ResponseMode responseMode = service.resolveResponseMode(intentResolution, session);
        return Map.of(
                "sessionId", sessionId,
                "objective", objective,
                GRAPH_PLAN_ID_KEY, planId,
                GRAPH_WORKFLOW_STEPS_KEY, steps,
                GRAPH_RESPONSE_MODE_KEY, responseMode == null ? ResponseMode.WORKFLOW_SUMMARY : responseMode
        );
    }

    Map<String, Object> executePlannedStepsNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        String objective = state.value("objective", String.class).orElseThrow();
        String planId = state.value(GRAPH_PLAN_ID_KEY, String.class).orElseThrow();
        List<WorkflowStep> steps = state.value(GRAPH_WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .orElseThrow();
        List<WorkflowStep> executedSteps = service.executeStepsWithStatusTracking(sessionId, planId, steps, objective, 0);
        return service.buildPostExecutionState(
                sessionId,
                objective,
                planId,
                executedSteps,
                state.value(GRAPH_RESPONSE_MODE_KEY, ResponseMode.class).orElse(ResponseMode.WORKFLOW_SUMMARY)
        );
    }

    Map<String, Object> executeContinuationStepsNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        String objective = state.value("objective", String.class).orElseThrow();
        String planId = state.value(GRAPH_PLAN_ID_KEY, String.class).orElseThrow();
        int fromIndex = state.value(GRAPH_START_INDEX_KEY, Integer.valueOf(0));
        List<WorkflowStep> workflowSteps = state.value(GRAPH_WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .orElseThrow();
        List<WorkflowStep> allSteps = service.executeStepsWithStatusTracking(sessionId, planId, workflowSteps, objective, fromIndex);
        return service.buildPostExecutionState(
                sessionId,
                objective,
                planId,
                allSteps,
                state.value(GRAPH_RESPONSE_MODE_KEY, ResponseMode.class).orElse(ResponseMode.WORKFLOW_SUMMARY)
        );
    }

    String selectPostExecutionTransition(OverAllState state) {
        if (state.value(GRAPH_PENDING_FEEDBACK_KEY, Object.class)
                .map(service::coerceHumanFeedbackRequest)
                .isPresent()) {
            return "paused";
        }
        if (state.value(GRAPH_FAILED_STEP_KEY, Object.class)
                .map(service::coerceWorkflowStep)
                .isPresent()) {
            return "failed";
        }
        return "completed";
    }

    Map<String, Object> returnGraphResponseNode(OverAllState state) {
        WorkflowExecutionResponse response = state.value(GRAPH_RESPONSE_KEY, Object.class)
                .map(service::coerceWorkflowExecutionResponse)
                .orElseThrow();
        return Map.of(GRAPH_RESPONSE_KEY, response);
    }

    boolean hasGraphResponse(OverAllState state) {
        return state.value(GRAPH_RESPONSE_KEY, Object.class)
                .map(service::coerceWorkflowExecutionResponse)
                .isPresent();
    }

    Map<String, Object> returnPausedAfterRunNode(OverAllState state) {
        String objective = state.value("objective", String.class).orElseThrow();
        List<WorkflowStep> steps = state.value(GRAPH_WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .orElseThrow();
        HumanFeedbackRequest pendingFeedback = state.value(GRAPH_PENDING_FEEDBACK_KEY, Object.class)
                .map(service::coerceHumanFeedbackRequest)
                .orElseThrow();
        return Map.of(GRAPH_RESPONSE_KEY, service.createPausedResponse(objective, steps, pendingFeedback));
    }

    Map<String, Object> returnFailedAfterRunNode(OverAllState state) {
        String objective = state.value("objective", String.class).orElseThrow();
        List<WorkflowStep> steps = state.value(GRAPH_WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .orElseThrow();
        WorkflowStep failedStep = state.value(GRAPH_FAILED_STEP_KEY, Object.class)
                .map(service::coerceWorkflowStep)
                .orElseThrow();
        return Map.of(GRAPH_RESPONSE_KEY, service.createFailedResponse(objective, steps, failedStep));
    }

    Map<String, Object> finalizeSuccessfulRunNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        String planId = state.value(GRAPH_PLAN_ID_KEY, String.class).orElseThrow();
        String objective = state.value("objective", String.class).orElseThrow();
        List<WorkflowStep> steps = state.value(GRAPH_WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .orElseThrow();
        ResponseMode responseMode = state.value(GRAPH_RESPONSE_MODE_KEY, ResponseMode.class).orElse(ResponseMode.WORKFLOW_SUMMARY);
        return Map.of(GRAPH_RESPONSE_KEY, service.finalizeSuccessfulExecution(sessionId, planId, objective, steps, responseMode));
    }

    Map<String, Object> maybeSkipOutputEvaluationNode(OverAllState state) {
        String objective = state.value("objective", String.class).orElseThrow();
        ResponseMode responseMode = state.value(GRAPH_RESPONSE_MODE_KEY, ResponseMode.class).orElse(ResponseMode.WORKFLOW_SUMMARY);
        List<WorkflowStep> currentSteps = state.value(GRAPH_WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .orElse(List.of());
        if (!service.shouldEvaluateOutput(responseMode)) {
            OutputEvaluationResult outputEvaluation = new OutputEvaluationResult(
                    OutputEvaluationStatus.SKIPPED,
                    "",
                    List.of(),
                    null,
                    0,
                    WorkflowService.DEFAULT_OUTPUT_EVALUATION_MAX_RETRIES
            );
            return Map.of(
                    GRAPH_RESPONSE_KEY,
                    service.createCompletedResponse(objective, currentSteps, responseMode, outputEvaluation)
            );
        }
        return Map.of();
    }

    Map<String, Object> evaluateCurrentOutputNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        String objective = state.value("objective", String.class).orElseThrow();
        ResponseMode responseMode = state.value(GRAPH_RESPONSE_MODE_KEY, ResponseMode.class).orElse(ResponseMode.WORKFLOW_SUMMARY);
        int retryCount = state.value(GRAPH_OUTPUT_EVALUATION_RETRY_COUNT_KEY, Integer.valueOf(0));
        List<WorkflowStep> currentSteps = state.value(GRAPH_WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .orElse(List.of());
        WorkflowService.OutputEvaluationDecision decision = service.evaluateOutput(objective, currentSteps, responseMode);
        SessionState session = service.sessionMemoryService().getOrCreate(sessionId);
        session.addExecutionLog("Output evaluation: " + decision.status() + " | " + decision.message());
        return Map.of(
                GRAPH_OUTPUT_EVALUATION_DECISION_KEY, decision,
                GRAPH_OUTPUT_EVALUATION_RESULT_KEY, decision.toResult(retryCount, WorkflowService.DEFAULT_OUTPUT_EVALUATION_MAX_RETRIES, false)
        );
    }

    String selectOutputEvaluationTransition(OverAllState state) {
        String objective = state.value("objective", String.class).orElseThrow();
        List<WorkflowStep> currentSteps = state.value(GRAPH_WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .orElse(List.of());
        WorkflowService.OutputEvaluationDecision decision = state.value(GRAPH_OUTPUT_EVALUATION_DECISION_KEY, WorkflowService.OutputEvaluationDecision.class).orElseThrow();
        int retryCount = state.value(GRAPH_OUTPUT_EVALUATION_RETRY_COUNT_KEY, Integer.valueOf(0));
        if (decision.status() == OutputEvaluationStatus.PASSED
                || decision.status() == OutputEvaluationStatus.MINOR_ISSUES) {
            return "complete";
        }
        if (service.shouldRetryAfterEvaluation(decision, retryCount)) {
            return "retry";
        }
        if (decision.status() == OutputEvaluationStatus.MAJOR_ISSUES
                || (decision.status() == OutputEvaluationStatus.ASK_USER && service.hasCoreDeliverable(objective, currentSteps))) {
            return "complete";
        }
        return "fail";
    }

    Map<String, Object> retryOutputWorkflowNode(OverAllState state) {
        String sessionId = state.value("sessionId", String.class).orElseThrow();
        String planId = state.value(GRAPH_PLAN_ID_KEY, String.class).orElseThrow();
        String objective = state.value("objective", String.class).orElseThrow();
        ResponseMode responseMode = state.value(GRAPH_RESPONSE_MODE_KEY, ResponseMode.class).orElse(ResponseMode.WORKFLOW_SUMMARY);
        WorkflowService.OutputEvaluationDecision decision = state.value(GRAPH_OUTPUT_EVALUATION_DECISION_KEY, WorkflowService.OutputEvaluationDecision.class).orElseThrow();
        int retryCount = state.value(GRAPH_OUTPUT_EVALUATION_RETRY_COUNT_KEY, Integer.valueOf(0)) + 1;
        List<WorkflowStep> currentSteps = state.value(GRAPH_WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .orElse(List.of());

        SessionState session = service.sessionMemoryService().getOrCreate(sessionId);
        session.addExecutionLog("Output evaluation requested automatic retry #" + retryCount);
        if (decision.revisionPrompt() != null && !decision.revisionPrompt().isBlank()) {
            session.addMessage("system", "[OUTPUT_EVALUATION_RETRY] " + decision.revisionPrompt());
        }

        int retryStartIndex = service.clampRetryStartIndex(currentSteps, decision.retryStartIndex());
        List<WorkflowStep> retriedSteps = service.prepareStepsForOutputRetry(currentSteps, decision, retryCount);
        retriedSteps = service.executeStepsWithStatusTracking(
                sessionId,
                planId,
                retriedSteps,
                objective,
                retryStartIndex
        );

        OutputEvaluationResult outputEvaluation = decision.toResult(retryCount, WorkflowService.DEFAULT_OUTPUT_EVALUATION_MAX_RETRIES, false);
        Optional<HumanFeedbackRequest> pendingFeedback = service.sessionMemoryService().getPendingFeedback(sessionId);
        if (pendingFeedback.isPresent()) {
            return Map.of(
                    GRAPH_RESPONSE_KEY,
                    service.createPausedResponse(objective, retriedSteps, pendingFeedback.get(), outputEvaluation)
            );
        }
        Optional<WorkflowStep> failedStep = service.findFailedStep(retriedSteps);
        if (failedStep.isPresent()) {
            return Map.of(
                    GRAPH_RESPONSE_KEY,
                    service.createFailedResponse(objective, retriedSteps, failedStep.get(), outputEvaluation)
            );
        }
        return Map.of(
                GRAPH_WORKFLOW_STEPS_KEY, retriedSteps,
                GRAPH_RESPONSE_MODE_KEY, responseMode,
                GRAPH_OUTPUT_EVALUATION_RETRY_COUNT_KEY, retryCount,
                GRAPH_OUTPUT_EVALUATION_RESULT_KEY, outputEvaluation
        );
    }

    Map<String, Object> completeWithCurrentOutputNode(OverAllState state) {
        String objective = state.value("objective", String.class).orElseThrow();
        ResponseMode responseMode = state.value(GRAPH_RESPONSE_MODE_KEY, ResponseMode.class).orElse(ResponseMode.WORKFLOW_SUMMARY);
        int retryCount = state.value(GRAPH_OUTPUT_EVALUATION_RETRY_COUNT_KEY, Integer.valueOf(0));
        WorkflowService.OutputEvaluationDecision decision = state.value(GRAPH_OUTPUT_EVALUATION_DECISION_KEY, WorkflowService.OutputEvaluationDecision.class).orElseThrow();
        List<WorkflowStep> currentSteps = state.value(GRAPH_WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .orElse(List.of());
        OutputEvaluationResult outputEvaluation = decision.toResult(
                retryCount,
                WorkflowService.DEFAULT_OUTPUT_EVALUATION_MAX_RETRIES,
                decision.status() == OutputEvaluationStatus.MAJOR_ISSUES
                        || decision.status() == OutputEvaluationStatus.ASK_USER
        );
        return Map.of(
                GRAPH_RESPONSE_KEY,
                service.createCompletedResponse(objective, currentSteps, responseMode, outputEvaluation)
        );
    }

    Map<String, Object> failFromOutputEvaluationNode(OverAllState state) {
        String objective = state.value("objective", String.class).orElseThrow();
        int retryCount = state.value(GRAPH_OUTPUT_EVALUATION_RETRY_COUNT_KEY, Integer.valueOf(0));
        WorkflowService.OutputEvaluationDecision decision = state.value(GRAPH_OUTPUT_EVALUATION_DECISION_KEY, WorkflowService.OutputEvaluationDecision.class).orElseThrow();
        List<WorkflowStep> currentSteps = state.value(GRAPH_WORKFLOW_STEPS_KEY, Object.class)
                .map(service::coerceWorkflowSteps)
                .orElse(List.of());
        OutputEvaluationResult outputEvaluation = decision.toResult(retryCount, WorkflowService.DEFAULT_OUTPUT_EVALUATION_MAX_RETRIES, true);
        return Map.of(
                GRAPH_RESPONSE_KEY,
                service.createOutputEvaluationFailureResponse(objective, currentSteps, outputEvaluation)
        );
    }
}
