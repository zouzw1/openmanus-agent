package com.openmanus.saa.service;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.HumanFeedbackResponse;
import com.openmanus.saa.model.InferencePolicy;
import com.openmanus.saa.model.WorkflowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 工作流检查点服务，封装框架原生的 MemorySaver 和 CompiledGraph 状态管理 API。
 * 作为工作流暂停/继续机制的唯一状态来源，统一管理中断状态。
 *
 * <p>设计原则：
 * <ul>
 *   <li>所有工作流状态（步骤列表、pendingFeedback、intentResolution 等）存储在框架的 MemorySaver 中</li>
 *   <li>通过 threadId（即 sessionId）隔离不同会话的状态</li>
 *   <li>外部 Session 只存储消息历史和用户偏好，与工作流状态分离</li>
 * </ul>
 */
@Service
public class WorkflowCheckpointService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowCheckpointService.class);

    public static final String PENDING_FEEDBACK_KEY = "pendingFeedback";
    public static final String WORKFLOW_STEPS_KEY = "workflowSteps";
    public static final String CURRENT_STEP_INDEX_KEY = "currentStepIndex";
    public static final String EXECUTED_STEP_COUNT_KEY = "executedStepCount";
    public static final String LOOP_CONTINUE_KEY = "loopContinue";
    public static final String OBJECTIVE_KEY = "objective";
    public static final String PLAN_ID_KEY = "planId";
    public static final String RESPONSE_MODE_KEY = "responseMode";
    public static final String INTENT_RESOLUTION_KEY = "intentResolution";
    public static final String FEEDBACK_WAIT_KEY = "feedbackWait";
    public static final String FEEDBACK_RESPONSE_KEY = "feedbackResponse";
    public static final String NEXT_NODE_KEY = "nextNode";
    public static final String WORKFLOW_RESPONSE_KEY = "workflowResponse";

    private final MemorySaver checkpointSaver;

    public WorkflowCheckpointService() {
        this.checkpointSaver = new MemorySaver();
        log.info("WorkflowCheckpointService initialized with MemorySaver");
    }

    /**
     * 获取检查点存储器，用于配置 CompiledGraph。
     */
    public MemorySaver getCheckpointSaver() {
        return checkpointSaver;
    }

    /**
     * 创建指定会话的运行配置。
     */
    public RunnableConfig createConfig(String sessionId) {
        return RunnableConfig.builder()
                .threadId(sessionId)
                .build();
    }

    /**
     * 创建恢复执行的运行配置。
     */
    public RunnableConfig createResumeConfig(String sessionId) {
        return RunnableConfig.builder()
                .threadId(sessionId)
                .resume()
                .build();
    }

    /**
     * 检查指定会话是否有中断的工作流。
     *
     * 注意：仅检查 checkpoint 是否存在是不够的，因为 graph 框架会在每个节点后保存状态。
     * 真正的"中断"应该满足：存在 checkpoint 且有 pendingFeedback。
     */
    public boolean isInterrupted(String sessionId) {
        return getPendingFeedback(sessionId).isPresent();
    }

    /**
     * 获取完整状态的原始数据。
     */
    public Optional<Map<String, Object>> getState(String sessionId) {
        RunnableConfig config = createConfig(sessionId);
        return checkpointSaver.get(config)
                .map(checkpoint -> checkpoint.getState());
    }

    /**
     * 获取中断时的待处理反馈。
     */
    public Optional<HumanFeedbackRequest> getPendingFeedback(String sessionId) {
        return getState(sessionId)
                .map(state -> state.get(PENDING_FEEDBACK_KEY))
                .filter(Objects::nonNull)
                .map(this::coerceHumanFeedbackRequest);
    }

    /**
     * 获取工作流步骤列表。
     */
    public Optional<List<WorkflowStep>> getWorkflowSteps(String sessionId) {
        return getState(sessionId)
                .map(state -> state.get(WORKFLOW_STEPS_KEY))
                .filter(Objects::nonNull)
                .map(this::coerceWorkflowSteps);
    }

    /**
     * 获取当前步骤索引。
     */
    public Optional<Integer> getCurrentStepIndex(String sessionId) {
        return getState(sessionId)
                .map(state -> state.get(CURRENT_STEP_INDEX_KEY))
                .filter(Integer.class::isInstance)
                .map(obj -> (Integer) obj);
    }

    /**
     * 获取目标。
     */
    public Optional<String> getObjective(String sessionId) {
        return getState(sessionId)
                .map(state -> (String) state.get(OBJECTIVE_KEY));
    }

    /**
     * 获取计划ID。
     */
    public Optional<String> getPlanId(String sessionId) {
        return getState(sessionId)
                .map(state -> (String) state.get(PLAN_ID_KEY));
    }

    /**
     * 更新状态。
     */
    public void updateState(String sessionId, Map<String, Object> updates) {
        RunnableConfig config = createConfig(sessionId);
        checkpointSaver.get(config).ifPresent(checkpoint -> {
            Map<String, Object> currentState = new HashMap<>(checkpoint.getState());
            currentState.putAll(updates);
            try {
                checkpointSaver.put(config, checkpoint.updateState(currentState, null));
                log.debug("Updated checkpoint state for session {}", sessionId);
            } catch (Exception e) {
                log.error("Failed to update checkpoint state for session {}", sessionId, e);
                throw new RuntimeException("Failed to update checkpoint state", e);
            }
        });
    }

    /**
     * 释放指定会话的检查点。
     */
    public void release(String sessionId) {
        RunnableConfig config = createConfig(sessionId);
        try {
            checkpointSaver.release(config);
            log.info("Released checkpoint for session {}", sessionId);
        } catch (Exception e) {
            log.warn("Failed to release checkpoint for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 注入用户反馈到状态。
     */
    public void injectFeedback(String sessionId, HumanFeedbackResponse feedback, HumanFeedbackRequest pendingFeedback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("sessionId", sessionId);  // 必须保存，resume 后 resolveFeedbackNode 需要从 state 中读取
        updates.put(FEEDBACK_RESPONSE_KEY, feedback);
        updates.put(PENDING_FEEDBACK_KEY, pendingFeedback);
        updates.put(FEEDBACK_WAIT_KEY, false);

        // 根据反馈动作决定下一步
        String nextNode = resolveNextNode(feedback);
        updates.put(NEXT_NODE_KEY, nextNode);

        updateState(sessionId, updates);
        log.info("Injected feedback for session {}, action={}, nextNode={}",
                sessionId, feedback.getAction(), nextNode);
    }

    /**
     * 保存待处理反馈到状态。
     */
    public void savePendingFeedback(String sessionId, HumanFeedbackRequest pendingFeedback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(PENDING_FEEDBACK_KEY, pendingFeedback);
        updates.put(FEEDBACK_WAIT_KEY, true);
        updates.put(CURRENT_STEP_INDEX_KEY, pendingFeedback.getStepIndex());
        updates.put(WORKFLOW_STEPS_KEY, pendingFeedback.getSteps());
        updateState(sessionId, updates);
        log.info("Saved pending feedback for session {}, stepIndex={}",
                sessionId, pendingFeedback.getStepIndex());
    }

    /**
     * 清除待处理反馈。
     */
    public void clearPendingFeedback(String sessionId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(PENDING_FEEDBACK_KEY, null);
        updates.put(FEEDBACK_WAIT_KEY, false);
        updates.put(FEEDBACK_RESPONSE_KEY, null);
        updateState(sessionId, updates);
    }

    /**
     * 判断是否正在等待反馈。
     */
    public boolean isWaitingForFeedback(String sessionId) {
        return getState(sessionId)
                .map(state -> state.get(FEEDBACK_WAIT_KEY))
                .filter(Boolean.class::isInstance)
                .map(obj -> (Boolean) obj)
                .orElse(false);
    }

    private String resolveNextNode(HumanFeedbackResponse feedback) {
        if (feedback.isReplanRequired()) {
            return "plan";
        }
        return switch (feedback.getAction()) {
            case ABORT_PLAN -> "abort";
            // SKIP_STEP: resolveFeedbackNode 已内部处理跳过逻辑，之后继续执行后续步骤
            case SKIP_STEP -> "execute";
            // RETRY/PROVIDE_INFO/MODIFY_AND_RETRY: 继续执行当前或后续步骤
            case RETRY, PROVIDE_INFO, MODIFY_AND_RETRY -> "execute";
        };
    }

    @SuppressWarnings("unchecked")
    private HumanFeedbackRequest coerceHumanFeedbackRequest(Object obj) {
        if (obj instanceof HumanFeedbackRequest) {
            return (HumanFeedbackRequest) obj;
        }
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            return new HumanFeedbackRequest(
                    (String) map.get("sessionId"),
                    (String) map.get("objective"),
                    (String) map.get("planId"),
                    ((Number) map.get("stepIndex")).intValue(),
                    coerceWorkflowStep(map.get("failedStep")),
                    coerceWorkflowStepsList(map.get("steps")),
                    (String) map.get("errorMessage"),
                    (String) map.get("suggestedAction")
            );
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    HumanFeedbackResponse coerceHumanFeedbackResponse(Object obj) {
        if (obj instanceof HumanFeedbackResponse) {
            return (HumanFeedbackResponse) obj;
        }
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            String actionStr = (String) map.get("action");
            HumanFeedbackResponse.ActionType action = actionStr != null
                    ? HumanFeedbackResponse.ActionType.valueOf(actionStr)
                    : HumanFeedbackResponse.ActionType.PROVIDE_INFO;
            String providedInfo = (String) map.get("providedInfo");
            String modifiedParams = (String) map.get("modifiedParams");
            InferencePolicy inferencePolicy = coerceInferencePolicy(map.get("inferencePolicy"));
            boolean replanRequired = map.get("replanRequired") instanceof Boolean b && b;
            String updatedObjective = (String) map.get("updatedObjective");
            return new HumanFeedbackResponse(action, providedInfo, modifiedParams, inferencePolicy, replanRequired, updatedObjective);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private InferencePolicy coerceInferencePolicy(Object obj) {
        if (obj instanceof InferencePolicy) {
            return (InferencePolicy) obj;
        }
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            return new InferencePolicy(
                    map.get("inferenceAllowed") instanceof Boolean b && b,
                    (String) map.get("inferenceScope"),
                    coerceStringList(map.get("providedFacts")),
                    coerceStringList(map.get("delegatedFields")),
                    coerceStringList(map.get("mustConfirmFields")),
                    (String) map.get("rationale")
            );
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> coerceStringList(Object obj) {
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String s && !s.isBlank()) {
                    result.add(s);
                }
            }
            return List.copyOf(result);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<WorkflowStep> coerceWorkflowSteps(Object obj) {
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            if (list.isEmpty() || list.get(0) instanceof WorkflowStep) {
                return (List<WorkflowStep>) obj;
            }
            return list.stream()
                    .map(this::coerceWorkflowStep)
                    .toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<WorkflowStep> coerceWorkflowStepsList(Object obj) {
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            return list.stream()
                    .map(this::coerceWorkflowStep)
                    .toList();
        }
        return List.of();
    }

    private WorkflowStep coerceWorkflowStep(Object obj) {
        if (obj instanceof WorkflowStep) {
            return (WorkflowStep) obj;
        }
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            return new WorkflowStep(
                    (String) map.get("agent"),
                    (String) map.get("description"),
                    map.get("requiredTools") != null ? (List<String>) map.get("requiredTools") : List.of(),
                    map.get("usedTools") != null ? (List<String>) map.get("usedTools") : List.of(),
                    map.get("parameterContext") != null ? (Map<String, Object>) map.get("parameterContext") : Map.of(),
                    map.get("status") != null ? com.openmanus.saa.model.StepStatus.valueOf(map.get("status").toString())
                            : com.openmanus.saa.model.StepStatus.NOT_STARTED,
                    (String) map.get("result"),
                    null,
                    null,
                    null,
                    0,
                    false
            );
        }
        return null;
    }
}
