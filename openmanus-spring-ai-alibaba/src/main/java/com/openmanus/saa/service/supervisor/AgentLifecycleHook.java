package com.openmanus.saa.service.supervisor;

import com.openmanus.saa.model.AgentTask;
import com.openmanus.saa.model.AgentType;

/**
 * Lifecycle hook interface for agent events in multi-agent execution.
 * Implementations can react to various agent lifecycle events.
 */
public interface AgentLifecycleHook {

    default void onAgentStarted(AgentPeer peer) {}

    default void onAgentStopped(AgentPeer peer) {}

    default void onTaskAssigned(AgentPeer peer, AgentTask task) {}

    default void onTaskStarted(AgentPeer peer, AgentTask task) {}

    default void onTaskCompleted(AgentPeer peer, AgentTask task, TaskExecutionResult result) {}

    default void onMessageReceived(AgentPeer peer, AgentMessage message) {}

    default void onError(AgentPeer peer, AgentTask task, Throwable error) {}

    default void onContextSummarized(AgentPeer peer, int originalLength, int summaryLength, String summary) {}

    default void onBackgroundTaskStarted(AgentPeer peer, String taskId, AgentType agentType) {}

    default void onBackgroundTaskCompleted(AgentPeer peer, String taskId, TaskExecutionResult result) {}

    default void onBackgroundTaskFailed(AgentPeer peer, String taskId, Throwable error, boolean cancelled) {}

    default void onToolAccessDenied(AgentPeer peer, String toolName, String reason) {}

    default void onIsolationActivated(AgentPeer peer, String isolationType, String isolatedPath) {}

    default int getOrder() { return 0; }
}
