package com.openmanus.saa.integration;

import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.context.WorkflowState;
import com.openmanus.saa.model.session.Session;
import com.openmanus.saa.service.session.SessionMemoryService;
import com.openmanus.saa.service.session.storage.MemorySessionStorage;
import com.openmanus.saa.config.SessionConfig;
import com.openmanus.saa.service.session.SessionCompactor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 对话上下文感知系统的集成测试。
 */
class ConversationContextIntegrationTest {

    private SessionMemoryService sessionMemoryService;
    private Session session;

    @BeforeEach
    void setUp() {
        MemorySessionStorage storage = new MemorySessionStorage();
        SessionCompactor compactor = new SessionCompactor();
        SessionConfig config = new SessionConfig();
        sessionMemoryService = new SessionMemoryService(storage, compactor, config);
        session = sessionMemoryService.getOrCreate("test-integration-session");
    }

    @Test
    void testUserPreferencePersistence() {
        String sessionId = "test-pref-session";

        // 先创建 session
        sessionMemoryService.getOrCreate(sessionId);

        // 保存偏好
        sessionMemoryService.saveUserPreference(sessionId, "diet", "喜欢吃辣");

        // 验证偏好持久化
        Optional<Object> pref = sessionMemoryService.getUserPreference(sessionId, "diet");
        assertTrue(pref.isPresent());
        assertEquals("喜欢吃辣", pref.get());
    }

    @Test
    void testMultipleUserPreferences() {
        String sessionId = "test-multi-pref-session";

        // 先创建 session
        sessionMemoryService.getOrCreate(sessionId);

        // 保存多个偏好
        sessionMemoryService.saveUserPreference(sessionId, "diet", "喜欢吃辣");
        sessionMemoryService.saveUserPreference(sessionId, "budget", "中等");
        sessionMemoryService.saveUserPreference(sessionId, "travel_style", "深度游");

        // 验证所有偏好
        Map<String, Object> prefs = sessionMemoryService.getUserPreferences(sessionId);
        assertEquals(3, prefs.size());
        assertEquals("喜欢吃辣", prefs.get("diet"));
        assertEquals("中等", prefs.get("budget"));
        assertEquals("深度游", prefs.get("travel_style"));
    }

    @Test
    void testWorkflowStatePersistence() {
        String sessionId = "test-workflow-session";

        // 先创建 session
        sessionMemoryService.getOrCreate(sessionId);

        // 保存工作流状态
        WorkflowState state = WorkflowState.completed("旅行计划", "plan-123", "详细行程");
        sessionMemoryService.saveWorkflowState(sessionId, state);

        // 验证状态持久化
        Optional<WorkflowState> retrieved = sessionMemoryService.getWorkflowState(sessionId);
        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get().isCompleted());
        assertEquals("详细行程", retrieved.get().lastDeliverable());
    }

    @Test
    void testPendingFeedbackPersistence() {
        String sessionId = "test-feedback-session";

        // 先创建 session
        sessionMemoryService.getOrCreate(sessionId);

        // 保存待处理反馈
        HumanFeedbackRequest feedback = new HumanFeedbackRequest(
            sessionId, "旅行计划", "plan-123", 2, null, null, "缺少参数", "补充参数"
        );
        sessionMemoryService.savePendingFeedback(sessionId, feedback);

        // 验证反馈持久化
        Optional<HumanFeedbackRequest> pending = sessionMemoryService.getPendingFeedback(sessionId);
        assertTrue(pending.isPresent());
        assertEquals(2, pending.get().getStepIndex());
        assertEquals("旅行计划", pending.get().getObjective());
    }

    @Test
    void testHasPendingFeedback_whenPaused_shouldReturnTrue() {
        String sessionId = "test-has-feedback-session";

        // 先创建 session
        sessionMemoryService.getOrCreate(sessionId);

        // 保存暂停状态
        HumanFeedbackRequest feedback = new HumanFeedbackRequest(
            sessionId, "旅行计划", "plan-123", 2, null, null, "缺少参数", "补充参数"
        );
        sessionMemoryService.savePendingFeedback(sessionId, feedback);

        // 验证 hasPendingFeedback
        assertTrue(sessionMemoryService.hasPendingFeedback(sessionId));
    }

    @Test
    void testWorkflowStateClear() {
        String sessionId = "test-clear-session";

        // 先创建 session
        sessionMemoryService.getOrCreate(sessionId);

        // 保存并清除工作流状态
        WorkflowState state = WorkflowState.completed("旅行计划", "plan-123", "详细行程");
        sessionMemoryService.saveWorkflowState(sessionId, state);
        sessionMemoryService.clearWorkflowState(sessionId);

        // 验证已清除
        Optional<WorkflowState> retrieved = sessionMemoryService.getWorkflowState(sessionId);
        assertFalse(retrieved.isPresent());
    }

    @Test
    void testWorkflowStateTransitions() {
        String sessionId = "test-transitions-session";

        // 先创建 session
        sessionMemoryService.getOrCreate(sessionId);

        // IN_PROGRESS
        WorkflowState inProgress = WorkflowState.inProgress("旅行计划", "plan-123", null);
        sessionMemoryService.saveWorkflowState(sessionId, inProgress);
        assertTrue(sessionMemoryService.getWorkflowState(sessionId).get().status() == WorkflowState.WorkflowStatus.IN_PROGRESS);

        // PAUSED
        HumanFeedbackRequest feedback = new HumanFeedbackRequest(
            sessionId, "旅行计划", "plan-123", 2, null, null, "缺少参数", "补充参数"
        );
        WorkflowState paused = WorkflowState.paused("旅行计划", "plan-123", 2, null, feedback);
        sessionMemoryService.saveWorkflowState(sessionId, paused);
        assertTrue(sessionMemoryService.hasPendingFeedback(sessionId));

        // COMPLETED
        WorkflowState completed = WorkflowState.completed("旅行计划", "plan-123", "详细行程");
        sessionMemoryService.saveWorkflowState(sessionId, completed);
        assertFalse(sessionMemoryService.hasPendingFeedback(sessionId));
        assertTrue(sessionMemoryService.getWorkflowState(sessionId).get().isCompleted());
    }
}