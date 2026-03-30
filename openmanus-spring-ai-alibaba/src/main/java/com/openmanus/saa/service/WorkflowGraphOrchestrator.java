package com.openmanus.saa.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.openmanus.saa.model.HumanFeedbackResponse;
import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.ResponseMode;
import com.openmanus.saa.model.WorkflowExecutionResponse;
import com.openmanus.saa.model.WorkflowStep;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class WorkflowGraphOrchestrator {

    private static final String RESPONSE_KEY = "workflowResponse";
    private static final String PENDING_FEEDBACK_KEY = "pendingFeedback";
    private static final String INTENT_RESOLUTION_KEY = "intentResolution";
    private static final String PLAN_ID_KEY = "planId";
    private static final String WORKFLOW_STEPS_KEY = "workflowSteps";
    private static final String FAILED_STEP_KEY = "failedStep";
    private static final String RESPONSE_MODE_KEY = "responseMode";
    private static final String START_INDEX_KEY = "startIndex";
    private static final String OUTPUT_EVALUATION_RETRY_COUNT_KEY = "outputEvaluationRetryCount";
    private static final String CURRENT_STEP_INDEX_KEY = "currentStepIndex";
    private static final String EXECUTED_STEP_COUNT_KEY = "executedStepCount";
    private static final String LOOP_CONTINUE_KEY = "loopContinue";

    private final WorkflowService delegate;
    private volatile CompiledGraph executionEntryGraph;
    private volatile CompiledGraph feedbackEntryGraph;
    private volatile CompiledGraph newPlanGraph;
    private volatile CompiledGraph continueExecutionGraph;
    private volatile CompiledGraph outputEvaluationGraph;
    private volatile CompiledGraph stepExecutionGraph;

    WorkflowGraphOrchestrator(WorkflowService delegate) {
        this.delegate = delegate;
    }

    WorkflowExecutionResponse invokeExecutionEntryGraph(String sessionId, String objective, IntentResolution providedIntentResolution) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("sessionId", sessionId);
            input.put("objective", objective);
            if (providedIntentResolution != null) {
                input.put(INTENT_RESOLUTION_KEY, providedIntentResolution);
            }
            Optional<OverAllState> state = getOrCreateExecutionEntryGraph().invoke(input);
            return extractResponse(state, "Execution graph did not produce a workflow response");
        } catch (Exception ex) {
            throw new IllegalStateException("Workflow execution graph failed", ex);
        }
    }

    WorkflowExecutionResponse invokeFeedbackEntryGraph(String sessionId, HumanFeedbackResponse feedback) {
        try {
            Optional<OverAllState> state = getOrCreateFeedbackEntryGraph().invoke(Map.of(
                    "sessionId", sessionId,
                    "feedback", feedback
            ));
            return extractResponse(state, "Feedback graph did not produce a workflow response");
        } catch (Exception ex) {
            throw new IllegalStateException("Workflow feedback graph failed", ex);
        }
    }

    WorkflowExecutionResponse invokeNewPlanGraph(String sessionId, String objective, IntentResolution intentResolution) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("sessionId", sessionId);
            input.put("objective", objective);
            if (intentResolution != null) {
                input.put(INTENT_RESOLUTION_KEY, intentResolution);
            }
            Optional<OverAllState> state = getOrCreateNewPlanGraph().invoke(input);
            return extractResponse(state, "New-plan graph did not produce a workflow response");
        } catch (Exception ex) {
            throw new IllegalStateException("Workflow new-plan graph failed", ex);
        }
    }

    WorkflowExecutionResponse invokeContinueExecutionGraph(
            String sessionId,
            String objective,
            String planId,
            List<WorkflowStep> workflowSteps,
            int fromIndex,
            ResponseMode responseMode
    ) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("sessionId", sessionId);
            input.put("objective", objective);
            input.put(PLAN_ID_KEY, planId);
            input.put(WORKFLOW_STEPS_KEY, workflowSteps);
            input.put(START_INDEX_KEY, fromIndex);
            input.put(RESPONSE_MODE_KEY, responseMode);
            Optional<OverAllState> state = getOrCreateContinueExecutionGraph().invoke(input);
            return extractResponse(state, "Continue graph did not produce a workflow response");
        } catch (Exception ex) {
            throw new IllegalStateException("Workflow continue graph failed", ex);
        }
    }

    WorkflowExecutionResponse invokeOutputEvaluationGraph(
            String sessionId,
            String planId,
            String objective,
            List<WorkflowStep> executedSteps,
            ResponseMode responseMode
    ) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("sessionId", sessionId);
            input.put(PLAN_ID_KEY, planId);
            input.put("objective", objective);
            input.put(WORKFLOW_STEPS_KEY, executedSteps == null ? List.of() : executedSteps);
            input.put(RESPONSE_MODE_KEY, responseMode);
            Optional<OverAllState> state = getOrCreateOutputEvaluationGraph().invoke(input);
            return extractResponse(state, "Output-evaluation graph did not produce a workflow response");
        } catch (Exception ex) {
            throw new IllegalStateException("Workflow output-evaluation graph failed", ex);
        }
    }

    List<WorkflowStep> invokeStepExecutionGraph(
            String sessionId,
            String planId,
            List<WorkflowStep> workflowSteps,
            String objective,
            int startIndexOffset
    ) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("sessionId", sessionId);
            input.put(PLAN_ID_KEY, planId);
            input.put("objective", objective);
            input.put(WORKFLOW_STEPS_KEY, delegate.copyWorkflowSteps(workflowSteps));
            input.put(CURRENT_STEP_INDEX_KEY, Math.max(0, startIndexOffset));
            input.put(EXECUTED_STEP_COUNT_KEY, 0);
            input.put(LOOP_CONTINUE_KEY, true);
            Optional<OverAllState> state = getOrCreateStepExecutionGraph().invoke(input);
            List<WorkflowStep> updatedSteps = state.flatMap(value -> value.value(WORKFLOW_STEPS_KEY, Object.class))
                    .map(delegate::coerceWorkflowSteps)
                    .orElseThrow(() -> new IllegalStateException("Step-execution graph did not return workflow steps"));
            return updatedSteps;
        } catch (Exception ex) {
            throw new IllegalStateException("Workflow step-execution graph failed", ex);
        }
    }

    private WorkflowExecutionResponse extractResponse(Optional<OverAllState> state, String errorMessage) {
        return state.flatMap(value -> value.value(RESPONSE_KEY, Object.class))
                .map(delegate::coerceWorkflowExecutionResponse)
                .orElseThrow(() -> new IllegalStateException(errorMessage));
    }

    private CompiledGraph getOrCreateExecutionEntryGraph() {
        return buildExecutionEntryGraph();
    }

    private CompiledGraph getOrCreateFeedbackEntryGraph() {
        return buildFeedbackEntryGraph();
    }

    private CompiledGraph getOrCreateNewPlanGraph() {
        return buildNewPlanGraph();
    }

    private CompiledGraph getOrCreateContinueExecutionGraph() {
        return buildContinueExecutionGraph();
    }

    private CompiledGraph getOrCreateOutputEvaluationGraph() {
        return buildOutputEvaluationGraph();
    }

    private CompiledGraph getOrCreateStepExecutionGraph() {
        return buildStepExecutionGraph();
    }

    private CompiledGraph buildExecutionEntryGraph() {
        try {
            StateGraph graph = new StateGraph();
            graph.addNode("checkPendingFeedback", state -> CompletableFuture.completedFuture(delegate.checkPendingFeedbackNode(state)));
            graph.addNode("resolveAndExecute", state -> CompletableFuture.completedFuture(delegate.resolveAndExecuteNode(state)));
            graph.addNode("returnPaused", state -> CompletableFuture.completedFuture(delegate.returnPausedNode(state)));
            graph.addEdge(StateGraph.START, "checkPendingFeedback");
            graph.addConditionalEdges(
                    "checkPendingFeedback",
                    state -> CompletableFuture.completedFuture(
                            state.value(PENDING_FEEDBACK_KEY, Object.class).isPresent() ? "returnPaused" : "resolveAndExecute"
                    ),
                    Map.of(
                            "returnPaused", "returnPaused",
                            "resolveAndExecute", "resolveAndExecute"
                    )
            );
            graph.addEdge("returnPaused", StateGraph.END);
            graph.addEdge("resolveAndExecute", StateGraph.END);
            return graph.compile();
        } catch (GraphStateException ex) {
            throw new IllegalStateException("Failed to build workflow execution graph", ex);
        }
    }

    private CompiledGraph buildFeedbackEntryGraph() {
        try {
            StateGraph graph = new StateGraph();
            graph.addNode("loadPendingFeedback", state -> CompletableFuture.completedFuture(delegate.loadPendingFeedbackNode(state)));
            graph.addNode("replanFromFeedback", state -> CompletableFuture.completedFuture(delegate.replanFromFeedbackNode(state)));
            graph.addNode("abortFromFeedback", state -> CompletableFuture.completedFuture(delegate.abortFromFeedbackNode(state)));
            graph.addNode("skipFromFeedback", state -> CompletableFuture.completedFuture(delegate.skipFromFeedbackNode(state)));
            graph.addNode("continueFromFeedback", state -> CompletableFuture.completedFuture(delegate.continueFromFeedbackNode(state)));
            graph.addEdge(StateGraph.START, "loadPendingFeedback");
            graph.addConditionalEdges(
                    "loadPendingFeedback",
                    state -> CompletableFuture.completedFuture(delegate.selectFeedbackTransition(state)),
                    Map.of(
                            "replan", "replanFromFeedback",
                            "abort", "abortFromFeedback",
                            "skip", "skipFromFeedback",
                            "continue", "continueFromFeedback"
                    )
            );
            graph.addEdge("replanFromFeedback", StateGraph.END);
            graph.addEdge("abortFromFeedback", StateGraph.END);
            graph.addEdge("skipFromFeedback", StateGraph.END);
            graph.addEdge("continueFromFeedback", StateGraph.END);
            return graph.compile();
        } catch (GraphStateException ex) {
            throw new IllegalStateException("Failed to build workflow feedback graph", ex);
        }
    }

    private CompiledGraph buildNewPlanGraph() {
        try {
            StateGraph graph = new StateGraph();
            graph.addNode("createPlanContext", state -> CompletableFuture.completedFuture(delegate.createPlanContextNode(state)));
            graph.addNode("executePlannedSteps", state -> CompletableFuture.completedFuture(delegate.executePlannedStepsNode(state)));
            graph.addNode("returnResponse", state -> CompletableFuture.completedFuture(delegate.returnGraphResponseNode(state)));
            graph.addNode("returnPausedAfterRun", state -> CompletableFuture.completedFuture(delegate.returnPausedAfterRunNode(state)));
            graph.addNode("returnFailedAfterRun", state -> CompletableFuture.completedFuture(delegate.returnFailedAfterRunNode(state)));
            graph.addNode("finalizeSuccessfulRun", state -> CompletableFuture.completedFuture(delegate.finalizeSuccessfulRunNode(state)));
            graph.addEdge(StateGraph.START, "createPlanContext");
            graph.addConditionalEdges(
                    "createPlanContext",
                    state -> CompletableFuture.completedFuture(delegate.hasGraphResponse(state) ? "returnResponse" : "executePlannedSteps"),
                    Map.of(
                            "returnResponse", "returnResponse",
                            "executePlannedSteps", "executePlannedSteps"
                    )
            );
            graph.addConditionalEdges(
                    "executePlannedSteps",
                    state -> CompletableFuture.completedFuture(delegate.selectPostExecutionTransition(state)),
                    Map.of(
                            "paused", "returnPausedAfterRun",
                            "failed", "returnFailedAfterRun",
                            "completed", "finalizeSuccessfulRun"
                    )
            );
            graph.addEdge("returnResponse", StateGraph.END);
            graph.addEdge("returnPausedAfterRun", StateGraph.END);
            graph.addEdge("returnFailedAfterRun", StateGraph.END);
            graph.addEdge("finalizeSuccessfulRun", StateGraph.END);
            return graph.compile();
        } catch (GraphStateException ex) {
            throw new IllegalStateException("Failed to build workflow new-plan graph", ex);
        }
    }

    private CompiledGraph buildContinueExecutionGraph() {
        try {
            StateGraph graph = new StateGraph();
            graph.addNode("executeContinuationSteps", state -> CompletableFuture.completedFuture(delegate.executeContinuationStepsNode(state)));
            graph.addNode("returnPausedAfterRun", state -> CompletableFuture.completedFuture(delegate.returnPausedAfterRunNode(state)));
            graph.addNode("returnFailedAfterRun", state -> CompletableFuture.completedFuture(delegate.returnFailedAfterRunNode(state)));
            graph.addNode("finalizeSuccessfulRun", state -> CompletableFuture.completedFuture(delegate.finalizeSuccessfulRunNode(state)));
            graph.addEdge(StateGraph.START, "executeContinuationSteps");
            graph.addConditionalEdges(
                    "executeContinuationSteps",
                    state -> CompletableFuture.completedFuture(delegate.selectPostExecutionTransition(state)),
                    Map.of(
                            "paused", "returnPausedAfterRun",
                            "failed", "returnFailedAfterRun",
                            "completed", "finalizeSuccessfulRun"
                    )
            );
            graph.addEdge("returnPausedAfterRun", StateGraph.END);
            graph.addEdge("returnFailedAfterRun", StateGraph.END);
            graph.addEdge("finalizeSuccessfulRun", StateGraph.END);
            return graph.compile();
        } catch (GraphStateException ex) {
            throw new IllegalStateException("Failed to build workflow continue graph", ex);
        }
    }

    private CompiledGraph buildOutputEvaluationGraph() {
        try {
            StateGraph graph = new StateGraph();
            graph.addNode("maybeSkipOutputEvaluation", state -> CompletableFuture.completedFuture(delegate.maybeSkipOutputEvaluationNode(state)));
            graph.addNode("evaluateCurrentOutput", state -> CompletableFuture.completedFuture(delegate.evaluateCurrentOutputNode(state)));
            graph.addNode("retryOutputWorkflow", state -> CompletableFuture.completedFuture(delegate.retryOutputWorkflowNode(state)));
            graph.addNode("completeWithCurrentOutput", state -> CompletableFuture.completedFuture(delegate.completeWithCurrentOutputNode(state)));
            graph.addNode("failFromOutputEvaluation", state -> CompletableFuture.completedFuture(delegate.failFromOutputEvaluationNode(state)));
            graph.addNode("returnGraphResponse", state -> CompletableFuture.completedFuture(delegate.returnGraphResponseNode(state)));
            graph.addEdge(StateGraph.START, "maybeSkipOutputEvaluation");
            graph.addConditionalEdges(
                    "maybeSkipOutputEvaluation",
                    state -> CompletableFuture.completedFuture(delegate.hasGraphResponse(state) ? "return" : "evaluate"),
                    Map.of(
                            "return", "returnGraphResponse",
                            "evaluate", "evaluateCurrentOutput"
                    )
            );
            graph.addConditionalEdges(
                    "evaluateCurrentOutput",
                    state -> CompletableFuture.completedFuture(delegate.selectOutputEvaluationTransition(state)),
                    Map.of(
                            "retry", "retryOutputWorkflow",
                            "complete", "completeWithCurrentOutput",
                            "fail", "failFromOutputEvaluation"
                    )
            );
            graph.addConditionalEdges(
                    "retryOutputWorkflow",
                    state -> CompletableFuture.completedFuture(delegate.hasGraphResponse(state) ? "return" : "evaluate"),
                    Map.of(
                            "return", "returnGraphResponse",
                            "evaluate", "evaluateCurrentOutput"
                    )
            );
            graph.addEdge("completeWithCurrentOutput", StateGraph.END);
            graph.addEdge("failFromOutputEvaluation", StateGraph.END);
            graph.addEdge("returnGraphResponse", StateGraph.END);
            return graph.compile();
        } catch (GraphStateException ex) {
            throw new IllegalStateException("Failed to build workflow output-evaluation graph", ex);
        }
    }

    private CompiledGraph buildStepExecutionGraph() {
        try {
            StateGraph graph = new StateGraph();
            graph.addNode("executeSingleStep", state -> CompletableFuture.completedFuture(delegate.executeSingleStepNode(state)));
            graph.addConditionalEdges(
                    "executeSingleStep",
                    state -> CompletableFuture.completedFuture(state.value(LOOP_CONTINUE_KEY, Boolean.FALSE) ? "loop" : "end"),
                    Map.of(
                            "loop", "executeSingleStep",
                            "end", StateGraph.END
                    )
            );
            graph.addEdge(StateGraph.START, "executeSingleStep");
            return graph.compile();
        } catch (GraphStateException ex) {
            throw new IllegalStateException("Failed to build workflow step-execution graph", ex);
        }
    }
}
