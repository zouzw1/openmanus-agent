package com.openmanus.saa.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.HumanFeedbackResponse;
import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.ResponseMode;
import com.openmanus.saa.model.WorkflowExecutionResponse;
import com.openmanus.saa.model.WorkflowStep;
import com.openmanus.saa.service.WorkflowCheckpointService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 统一工作流图编排器。
 *
 * <p>使用单一状态图替代之前的多个独立图，通过框架原生的中断机制实现暂停/继续。
 *
 * <p>图结构：
 * <pre>
 * START → plan → evaluatePlan → executeStep ──┬──→ finalizeOutput
 *                              │               │          │
 *                              │               │          ▼
 *                              │               │   evaluateOutput
 *                              │               │          │
 *                              │               │    ┌─────┴─────┐
 *                              │               │    │           │
 *                              └───────────────┴──►│    END     │
 *                                                 │ retryStep  │
 *                                                 └────────────┘
 *
 * RESUME (通过 RunnableConfig.resume() 触发)
 *     │
 *     ▼
 * resolveFeedback ────────────────────────────────────►
 *     │
 *     ├─ continue → executeStep (跳过已完成步骤)
 *     ├─ skip → executeStep (标记当前为 SKIPPED)
 *     ├─ replan → plan
 *     └─ abort → END
 * </pre>
 *
 * <p>中断机制：
 * <ul>
 *   <li>通过 CompileConfig.interruptsAfter("executeStep") 在步骤执行后中断</li>
 *   <li>节点内部检测 needsHumanFeedback 时，将 pendingFeedback 写入状态</li>
 *   <li>恢复时通过 RunnableConfig.resume() + updateState() 注入反馈数据</li>
 * </ul>
 */
final class WorkflowGraphOrchestrator {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(WorkflowGraphOrchestrator.class);

    private final WorkflowService delegate;
    private final WorkflowCheckpointService checkpointService;
    private final CompiledGraph unifiedGraph;

    WorkflowGraphOrchestrator(WorkflowService delegate, WorkflowCheckpointService checkpointService) {
        this.delegate = delegate;
        this.checkpointService = checkpointService;
        this.unifiedGraph = buildUnifiedGraph();
        log.info("UnifiedWorkflowGraph compiled successfully");
    }

    /**
     * 构建统一的工作流状态图。
     *
     * <p>关键修复：必须注册所有状态 keys 的 KeyStrategy，否则 resume 时
     * OverAllState.input() 方法会因为空的 keyStrategies 而过滤掉 checkpoint 的所有数据。
     * 参见 spring-ai-alibaba-graph 框架的 OverAllState.input() 实现。
     */
    private CompiledGraph buildUnifiedGraph() {
        try {
            // ========== KeyStrategy 注册（修复 resume 数据丢失问题）==========
            // 所有需要在 checkpoint 中持久化的状态 keys 都必须注册 KeyStrategy
            KeyStrategyFactory keyStrategyFactory = KeyStrategy.builder()
                    .addStrategy("sessionId")
                    .addStrategy("objective")
                    .addStrategy(WorkflowCheckpointService.PLAN_ID_KEY)
                    .addStrategy(WorkflowCheckpointService.WORKFLOW_STEPS_KEY)
                    .addStrategy(WorkflowCheckpointService.CURRENT_STEP_INDEX_KEY)
                    .addStrategy(WorkflowCheckpointService.EXECUTED_STEP_COUNT_KEY)
                    .addStrategy(WorkflowCheckpointService.LOOP_CONTINUE_KEY)
                    .addStrategy(WorkflowCheckpointService.PENDING_FEEDBACK_KEY)
                    .addStrategy(WorkflowCheckpointService.FEEDBACK_WAIT_KEY)
                    .addStrategy(WorkflowCheckpointService.FEEDBACK_RESPONSE_KEY)
                    .addStrategy(WorkflowCheckpointService.NEXT_NODE_KEY)
                    .addStrategy(WorkflowCheckpointService.INTENT_RESOLUTION_KEY)
                    .addStrategy(WorkflowCheckpointService.RESPONSE_MODE_KEY)
                    .addStrategy(WorkflowCheckpointService.WORKFLOW_RESPONSE_KEY)
                    .addStrategy("planEvaluationResult")
                    .addStrategy("outputEvaluation")
                    .defaultStrategy(KeyStrategy.REPLACE)
                    .build();

            StateGraph graph = new StateGraph(keyStrategyFactory);

            // ========== 节点定义 ==========

            // 规划节点
            graph.addNode("plan", state -> CompletableFuture.completedFuture(delegate.planNode(state)));

            // 计划评估节点
            graph.addNode("evaluatePlan", state -> CompletableFuture.completedFuture(delegate.evaluatePlanNode(state)));

            // 步骤执行节点（支持中断）
            graph.addNode("executeStep", state -> CompletableFuture.completedFuture(delegate.executeStepNode(state)));

            // 输出汇总节点
            graph.addNode("finalizeOutput", state -> CompletableFuture.completedFuture(delegate.finalizeOutputNode(state)));

            // 输出评估节点
            graph.addNode("evaluateOutput", state -> CompletableFuture.completedFuture(delegate.evaluateOutputNode(state)));

            // 重试步骤节点
            graph.addNode("retryStep", state -> CompletableFuture.completedFuture(delegate.retryStepNode(state)));

            // 反馈处理节点（RESUME 入口）
            graph.addNode("resolveFeedback", state -> CompletableFuture.completedFuture(delegate.resolveFeedbackNode(state)));

            // 终止节点
            graph.addNode("abortWorkflow", state -> CompletableFuture.completedFuture(delegate.abortWorkflowNode(state)));

            // 等待反馈节点（中断点）
            graph.addNode("waitForFeedback", state -> CompletableFuture.completedFuture(Map.of()));

            // 返回响应节点
            graph.addNode("returnResponse", state -> CompletableFuture.completedFuture(delegate.returnResponseNode(state)));

            // ========== 边定义 ==========

            // START -> plan
            graph.addEdge(StateGraph.START, "plan");

            // plan -> evaluatePlan / returnResponse (如果规划失败)
            graph.addConditionalEdges(
                    "plan",
                    state -> CompletableFuture.completedFuture(delegate.selectPlanTransition(state)),
                    Map.of(
                            "evaluate", "evaluatePlan",
                            "return", "returnResponse"
                    )
            );

            // evaluatePlan -> executeStep / finalizeOutput (如果快速路径) / waitForFeedback (需要修订plan)
            graph.addConditionalEdges(
                    "evaluatePlan",
                    state -> CompletableFuture.completedFuture(delegate.selectEvaluatePlanTransition(state)),
                    Map.of(
                            "execute", "executeStep",
                            "finalize", "finalizeOutput",
                            "planRevision", "waitForFeedback"
                    )
            );

            // executeStep -> executeStep (循环) / finalizeOutput / waitForFeedback (需要人工反馈时中断)
            graph.addConditionalEdges(
                    "executeStep",
                    state -> CompletableFuture.completedFuture(delegate.selectExecuteStepTransition(state)),
                    Map.of(
                            "loop", "executeStep",
                            "finalize", "finalizeOutput",
                            "interrupt", "waitForFeedback"
                    )
            );

            // finalizeOutput -> evaluateOutput
            graph.addEdge("finalizeOutput", "evaluateOutput");

            // evaluateOutput -> END / retryStep
            graph.addConditionalEdges(
                    "evaluateOutput",
                    state -> CompletableFuture.completedFuture(delegate.selectOutputEvaluationTransition(state)),
                    Map.of(
                            "complete", StateGraph.END,
                            "retry", "retryStep"
                    )
            );

            // retryStep -> executeStep
            graph.addEdge("retryStep", "executeStep");

            // resolveFeedback -> executeStep / plan / abortWorkflow
            graph.addConditionalEdges(
                    "resolveFeedback",
                    state -> CompletableFuture.completedFuture(delegate.selectFeedbackTransition(state)),
                    Map.of(
                            "execute", "executeStep",
                            "replan", "plan",
                            "abort", "abortWorkflow"
                    )
            );

            // abortWorkflow -> END
            graph.addEdge("abortWorkflow", StateGraph.END);

            // waitForFeedback -> resolveFeedback (恢复时从此节点继续)
            graph.addEdge("waitForFeedback", "resolveFeedback");

            // returnResponse -> END
            graph.addEdge("returnResponse", StateGraph.END);

            // ========== 编译配置 ==========
            // 注意：不使用 interruptsAfter，改用条件边控制流程
            // 当需要人工反馈时，selectExecuteStepTransition 会路由到 "interrupt"
            CompileConfig compileConfig = CompileConfig.builder()
                    .saverConfig(SaverConfig.builder()
                            .register(checkpointService.getCheckpointSaver())
                            .build())
                    .interruptsAfter(Set.of("waitForFeedback"))  // 只在等待反馈节点后中断
                    .build();

            return graph.compile(compileConfig);

        } catch (GraphStateException ex) {
            throw new IllegalStateException("Failed to build unified workflow graph", ex);
        }
    }

    /**
     * 执行新的工作流。
     */
    WorkflowExecutionResponse invoke(String sessionId, String objective, IntentResolution providedIntentResolution) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("sessionId", sessionId);
            input.put("objective", objective);
            if (providedIntentResolution != null) {
                input.put(WorkflowCheckpointService.INTENT_RESOLUTION_KEY, providedIntentResolution);
            }

            RunnableConfig config = checkpointService.createConfig(sessionId);
            Optional<OverAllState> state = unifiedGraph.invoke(input, config);
            return extractResponse(sessionId, state, "Workflow execution did not produce a response");
        } catch (Exception ex) {
            throw new IllegalStateException("Workflow execution failed", ex);
        }
    }

    /**
     * 从中断点恢复执行。
     */
    WorkflowExecutionResponse resume(String sessionId, HumanFeedbackResponse feedback) {
        try {
            // 获取待处理反馈
            HumanFeedbackRequest pendingFeedback = checkpointService.getPendingFeedback(sessionId)
                    .orElseThrow(() -> new IllegalStateException("No pending feedback found for session: " + sessionId));

            // 注入反馈到状态
            checkpointService.injectFeedback(sessionId, feedback, pendingFeedback);

            // 创建恢复配置
            RunnableConfig resumeConfig = checkpointService.createResumeConfig(sessionId);

            // 恢复执行
            Optional<OverAllState> state = unifiedGraph.invoke(Map.of(), resumeConfig);
            return extractResponse(sessionId, state, "Workflow resume did not produce a response");
        } catch (Exception ex) {
            throw new IllegalStateException("Workflow resume failed", ex);
        }
    }

    /**
     * 检查是否有中断的工作流。
     */
    boolean isInterrupted(String sessionId) {
        return checkpointService.isInterrupted(sessionId);
    }

    /**
     * 获取待处理反馈。
     */
    Optional<HumanFeedbackRequest> getPendingFeedback(String sessionId) {
        return checkpointService.getPendingFeedback(sessionId);
    }

    /**
     * 从状态中提取响应。优先从返回的状态中获取，若因中断而未包含响应，则从检查点中获取。
     */
    private WorkflowExecutionResponse extractResponse(String sessionId, Optional<OverAllState> state, String errorMessage) {
        // 1. 优先从返回的状态中获取（正常完成或执行失败的情况）
        Optional<Object> responseFromState = state.flatMap(s -> s.value(WorkflowCheckpointService.WORKFLOW_RESPONSE_KEY, Object.class));
        if (responseFromState.isPresent()) {
            return delegate.coerceWorkflowExecutionResponse(responseFromState.get());
        }

        // 2. 中断情况下，响应保存在检查点中（框架在 executeStep 后中断，未到达 finalizeOutput）
        Optional<Object> responseFromCheckpoint = checkpointService.getState(sessionId)
                .map(stateMap -> stateMap.get(WorkflowCheckpointService.WORKFLOW_RESPONSE_KEY))
                .filter(Objects::nonNull);
        if (responseFromCheckpoint.isPresent()) {
            return delegate.coerceWorkflowExecutionResponse(responseFromCheckpoint.get());
        }

        throw new IllegalStateException(errorMessage);
    }
}
