package com.openmanus.saa.model.context;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowStateTest {

    @Test
    void none_shouldReturnEmptyState() {
        WorkflowState state = WorkflowState.none();
        assertEquals(WorkflowState.WorkflowStatus.NONE, state.status());
        assertFalse(state.isPaused());
        assertFalse(state.hasActiveWorkflow());
    }

    @Test
    void paused_shouldReturnPausedState() {
        WorkflowState state = WorkflowState.paused("test objective", "plan-1", 2, null, null);
        assertTrue(state.isPaused());
        assertTrue(state.hasActiveWorkflow());
        assertEquals(2, state.currentStepIndex());
    }

    @Test
    void completed_shouldReturnCompletedState() {
        WorkflowState state = WorkflowState.completed("test objective", "plan-1", "deliverable content");
        assertTrue(state.isCompleted());
        assertEquals("deliverable content", state.lastDeliverable());
    }

    @Test
    void failed_shouldReturnFailedState() {
        WorkflowState state = WorkflowState.failed("test objective", "plan-1", 1, null);
        assertEquals(WorkflowState.WorkflowStatus.FAILED, state.status());
        assertTrue(state.hasActiveWorkflow());
    }

    @Test
    void inProgress_shouldReturnInProgressState() {
        WorkflowState state = WorkflowState.inProgress("test objective", "plan-1", null);
        assertEquals(WorkflowState.WorkflowStatus.IN_PROGRESS, state.status());
        assertNotNull(state.lastActiveAt());
    }
}
