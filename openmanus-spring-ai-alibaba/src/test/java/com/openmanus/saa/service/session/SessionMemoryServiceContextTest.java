package com.openmanus.saa.service.session;

import com.openmanus.saa.config.SessionConfig;
import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.context.WorkflowState;
import com.openmanus.saa.model.session.Session;
import com.openmanus.saa.service.session.storage.MemorySessionStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SessionMemoryServiceContextTest {

    private SessionMemoryService service;
    private Session session;

    @BeforeEach
    void setUp() {
        MemorySessionStorage storage = new MemorySessionStorage();
        SessionCompactor compactor = new SessionCompactor();
        SessionConfig config = new SessionConfig();
        service = new SessionMemoryService(storage, compactor, config);
        session = service.getOrCreate("test-session");
    }

    @Test
    void saveUserPreference_shouldPersist() {
        service.saveUserPreference("test-session", "diet", "喜欢吃辣");

        Optional<Object> pref = service.getUserPreference("test-session", "diet");
        assertTrue(pref.isPresent());
        assertEquals("喜欢吃辣", pref.get());
    }

    @Test
    void saveMultipleUserPreferences_shouldPersistAll() {
        service.saveUserPreference("test-session", "diet", "喜欢吃辣");
        service.saveUserPreference("test-session", "budget", "中等");

        Map<String, Object> prefs = service.getUserPreferences("test-session");
        assertEquals(2, prefs.size());
        assertEquals("喜欢吃辣", prefs.get("diet"));
        assertEquals("中等", prefs.get("budget"));
    }

    @Test
    void getUserPreference_nonExistent_shouldReturnEmpty() {
        Optional<Object> pref = service.getUserPreference("test-session", "nonexistent");
        assertFalse(pref.isPresent());
    }

    @Test
    void saveWorkflowState_shouldPersist() {
        WorkflowState state = WorkflowState.completed("旅行计划", "plan-123", "详细行程");
        service.saveWorkflowState("test-session", state);

        Optional<WorkflowState> retrieved = service.getWorkflowState("test-session");
        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get().isCompleted());
        assertEquals("详细行程", retrieved.get().lastDeliverable());
    }

    @Test
    void getWorkflowState_nonExistent_shouldReturnEmpty() {
        Optional<WorkflowState> state = service.getWorkflowState("test-session");
        assertFalse(state.isPresent());
    }

    @Test
    void clearWorkflowState_shouldRemove() {
        WorkflowState state = WorkflowState.completed("旅行计划", "plan-123", "详细行程");
        service.saveWorkflowState("test-session", state);
        service.clearWorkflowState("test-session");

        Optional<WorkflowState> retrieved = service.getWorkflowState("test-session");
        assertFalse(retrieved.isPresent());
    }

    @Test
    void savePendingFeedback_shouldPersistAsWorkflowState() {
        HumanFeedbackRequest feedback = new HumanFeedbackRequest(
            "test-session", "旅行计划", "plan-123", 2, null, null, "缺少参数", "补充参数"
        );
        service.savePendingFeedback("test-session", feedback);

        Optional<HumanFeedbackRequest> pending = service.getPendingFeedback("test-session");
        assertTrue(pending.isPresent());
        assertEquals(2, pending.get().getStepIndex());
    }

    @Test
    void getPendingFeedback_whenPaused_shouldReturn() {
        WorkflowState pausedState = WorkflowState.paused(
            "旅行计划", "plan-123", 2, null,
            new HumanFeedbackRequest("test-session", "旅行计划", "plan-123", 2, null, null, "缺少参数", "补充参数")
        );
        service.saveWorkflowState("test-session", pausedState);

        Optional<HumanFeedbackRequest> pending = service.getPendingFeedback("test-session");
        assertTrue(pending.isPresent());
    }

    @Test
    void getPendingFeedback_whenCompleted_shouldReturnEmpty() {
        WorkflowState completedState = WorkflowState.completed("旅行计划", "plan-123", "详细行程");
        service.saveWorkflowState("test-session", completedState);

        Optional<HumanFeedbackRequest> pending = service.getPendingFeedback("test-session");
        assertFalse(pending.isPresent());
    }

    @Test
    void hasPendingFeedback_whenPaused_shouldReturnTrue() {
        WorkflowState pausedState = WorkflowState.paused(
            "旅行计划", "plan-123", 2, null,
            new HumanFeedbackRequest("test-session", "旅行计划", "plan-123", 2, null, null, "缺少参数", "补充参数")
        );
        service.saveWorkflowState("test-session", pausedState);

        assertTrue(service.hasPendingFeedback("test-session"));
    }

    @Test
    void hasPendingFeedback_whenNoWorkflow_shouldReturnFalse() {
        assertFalse(service.hasPendingFeedback("test-session"));
    }
}