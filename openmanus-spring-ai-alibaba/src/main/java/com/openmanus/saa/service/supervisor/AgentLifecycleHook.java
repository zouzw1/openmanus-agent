package com.openmanus.saa.service.supervisor;

import com.openmanus.saa.model.AgentTask;

/**
 * Lifecycle hook interface for agent events in multi-agent execution.
 * Implementations can react to various agent lifecycle events.
 */
public interface AgentLifecycleHook {

    /**
     * Called when an agent peer is started.
     *
     * @param peer the agent peer that was started
     */
    default void onAgentStarted(AgentPeer peer) {
    }

    /**
     * Called when an agent peer is stopped.
     *
     * @param peer the agent peer that was stopped
     */
    default void onAgentStopped(AgentPeer peer) {
    }

    /**
     * Called when a task is started by an agent.
     *
     * @param peer the agent peer executing the task
     * @param task the task being started
     */
    default void onTaskStarted(AgentPeer peer, AgentTask task) {
    }

    /**
     * Called when a task is completed by an agent.
     *
     * @param peer the agent peer that completed the task
     * @param task the completed task
     * @param result the execution result
     */
    default void onTaskCompleted(AgentPeer peer, AgentTask task, TaskExecutionResult result) {
    }

    /**
     * Called when a message is received by an agent.
     *
     * @param peer the agent peer that received the message
     * @param message the received message
     */
    default void onMessageReceived(AgentPeer peer, AgentMessage message) {
    }

    /**
     * Called when an error occurs during task execution.
     *
     * @param peer the agent peer where the error occurred
     * @param task the task that caused the error (may be null)
     * @param error the error that occurred
     */
    default void onError(AgentPeer peer, AgentTask task, Throwable error) {
    }

    /**
     * Returns the order of this hook. Hooks with lower order values are executed first.
     * Default order is 0.
     *
     * @return the order value
     */
    default int getOrder() {
        return 0;
    }
}
