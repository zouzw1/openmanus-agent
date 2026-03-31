package com.openmanus.saa.service.supervisor;

import com.openmanus.saa.agent.AgentDefinition;
import com.openmanus.saa.model.AgentTask;
import com.openmanus.saa.model.WorkflowStep;
import com.openmanus.saa.service.agent.AgentExecutionResult;
import com.openmanus.saa.service.agent.SpecialistAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Independent Agent instance with its own context and message queue.
 * Represents a teammate in the multi-agent architecture.
 */
public class AgentPeer {

    private static final Logger log = LoggerFactory.getLogger(AgentPeer.class);

    private final String peerId;
    private final AgentDefinition definition;
    private final AgentContext context;
    private final AgentMessageQueue messageQueue;
    private final SpecialistAgent executor;
    private final SharedContextStore sharedContextStore;
    private final List<AgentLifecycleHook> lifecycleHooks;
    private final int taskTimeoutSeconds;
    private volatile boolean running = false;

    public AgentPeer(
            String peerId,
            AgentDefinition definition,
            SpecialistAgent executor,
            SharedContextStore sharedContextStore,
            int maxHistoryTurns,
            int maxContextChars,
            int messageQueueSize,
            int taskTimeoutSeconds,
            List<AgentLifecycleHook> lifecycleHooks
    ) {
        this.peerId = peerId != null ? peerId : UUID.randomUUID().toString();
        this.definition = definition;
        this.executor = executor;
        this.sharedContextStore = sharedContextStore;
        this.context = new AgentContext(this.peerId, maxHistoryTurns, maxContextChars);
        this.messageQueue = new AgentMessageQueue(this.peerId, messageQueueSize);
        this.taskTimeoutSeconds = taskTimeoutSeconds;
        this.lifecycleHooks = new CopyOnWriteArrayList<>(lifecycleHooks != null ? lifecycleHooks : List.of());
    }

    /**
     * Get the peer ID.
     *
     * @return the peer ID
     */
    public String getPeerId() {
        return peerId;
    }

    /**
     * Get the agent definition.
     *
     * @return the definition
     */
    public AgentDefinition getDefinition() {
        return definition;
    }

    /**
     * Get the agent context.
     *
     * @return the context
     */
    public AgentContext getContext() {
        return context;
    }

    /**
     * Get the message queue.
     *
     * @return the message queue
     */
    public AgentMessageQueue getMessageQueue() {
        return messageQueue;
    }

    /**
     * Get the specialist agent executor.
     *
     * @return the executor
     */
    public SpecialistAgent getExecutor() {
        return executor;
    }

    /**
     * Start this agent peer.
     */
    public void start() {
        if (!running) {
            running = true;
            log.info("Agent peer {} started", peerId);
            lifecycleHooks.forEach(hook -> {
                try {
                    hook.onAgentStarted(this);
                } catch (Exception e) {
                    log.warn("Lifecycle hook {} threw exception on start", hook.getClass().getSimpleName(), e);
                }
            });
        }
    }

    /**
     * Stop this agent peer.
     */
    public void stop() {
        if (running) {
            running = false;
            log.info("Agent peer {} stopped", peerId);
            lifecycleHooks.forEach(hook -> {
                try {
                    hook.onAgentStopped(this);
                } catch (Exception e) {
                    log.warn("Lifecycle hook {} threw exception on stop", hook.getClass().getSimpleName(), e);
                }
            });
        }
    }

    /**
     * Check if this peer is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Execute a task.
     *
     * @param task the task to execute
     * @return the execution result
     */
    public TaskExecutionResult executeTask(AgentTask task) {
        if (!running) {
            return TaskExecutionResult.failure(task.taskId(), peerId, "Agent peer is not running", 0);
        }

        Instant startTime = Instant.now();
        log.info("Agent peer {} starting task {}", peerId, task.taskId());

        // Notify hooks
        lifecycleHooks.forEach(hook -> {
            try {
                hook.onTaskStarted(this, task);
            } catch (Exception e) {
                log.warn("Lifecycle hook {} threw exception on task start", hook.getClass().getSimpleName(), e);
            }
        });

        try {
            // Add task context to conversation history
            context.addUserMessage(task.goal());

            // Resolve context references
            String resolvedContext = resolveContextRefs(task.contextRefs());
            if (!resolvedContext.isEmpty()) {
                context.addSystemMessage("Referenced context: " + resolvedContext);
            }

            // Execute with timeout
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> doExecute(task));

            String result;
            try {
                result = future.get(taskTimeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                long durationMs = Duration.between(startTime, Instant.now()).toMillis();
                log.warn("Task {} timed out after {}ms", task.taskId(), durationMs);
                TaskExecutionResult timeoutResult = TaskExecutionResult.timeout(task.taskId(), peerId, durationMs);
                notifyError(task, new TimeoutException("Task timed out after " + taskTimeoutSeconds + " seconds"));
                return timeoutResult;
            }

            long durationMs = Duration.between(startTime, Instant.now()).toMillis();
            TaskExecutionResult successResult = TaskExecutionResult.success(task.taskId(), peerId, result, durationMs);

            // Add result to context
            context.addAssistantMessage(result);

            // Notify hooks of completion
            lifecycleHooks.forEach(hook -> {
                try {
                    hook.onTaskCompleted(this, task, successResult);
                } catch (Exception e) {
                    log.warn("Lifecycle hook {} threw exception on task complete", hook.getClass().getSimpleName(), e);
                }
            });

            log.info("Agent peer {} completed task {} in {}ms", peerId, task.taskId(), durationMs);
            return successResult;

        } catch (Exception e) {
            long durationMs = Duration.between(startTime, Instant.now()).toMillis();
            log.error("Agent peer {} failed task {}: {}", peerId, task.taskId(), e.getMessage(), e);
            TaskExecutionResult failureResult = TaskExecutionResult.failure(task.taskId(), peerId, e.getMessage(), durationMs);
            notifyError(task, e);
            return failureResult;
        }
    }

    /**
     * Send a message to a specific agent.
     *
     * @param targetPeerId the target peer ID
     * @param message the message
     */
    public void sendMessage(String targetPeerId, AgentMessage message) {
        log.debug("Agent peer {} sending message to {}", peerId, targetPeerId);
        // Message routing is handled by the SupervisorAgentService
    }

    /**
     * Broadcast a message to all agents.
     *
     * @param content the message content
     */
    public void broadcast(String content) {
        AgentMessage broadcastMsg = AgentMessage.broadcast(peerId, content);
        log.debug("Agent peer {} broadcasting message", peerId);
        // Broadcast routing is handled by the SupervisorAgentService
    }

    /**
     * Receive a message from another agent.
     *
     * @param message the message to receive
     */
    public void receiveMessage(AgentMessage message) {
        messageQueue.offer(message);
        log.debug("Agent peer {} received message from {}", peerId, message.fromPeerId());

        // Notify hooks
        lifecycleHooks.forEach(hook -> {
            try {
                hook.onMessageReceived(this, message);
            } catch (Exception e) {
                log.warn("Lifecycle hook {} threw exception on message received", hook.getClass().getSimpleName(), e);
            }
        });
    }

    /**
     * Add a lifecycle hook.
     *
     * @param hook the hook to add
     */
    public void addLifecycleHook(AgentLifecycleHook hook) {
        lifecycleHooks.add(hook);
    }

    /**
     * Remove a lifecycle hook.
     *
     * @param hook the hook to remove
     */
    public void removeLifecycleHook(AgentLifecycleHook hook) {
        lifecycleHooks.remove(hook);
    }

    /**
     * Get the agent name from the definition.
     *
     * @return the agent name
     */
    public String getName() {
        return definition != null ? definition.getName() : "unknown";
    }

    /**
     * Get the agent ID from the definition.
     *
     * @return the agent ID
     */
    public String getAgentId() {
        return definition != null ? definition.getId() : "unknown";
    }

    private String doExecute(AgentTask task) {
        if (executor == null) {
            throw new IllegalStateException("No executor configured for agent peer " + peerId);
        }

        // Build context prompt
        String contextPrompt = context.buildContextPrompt();

        // Combine task goal with context
        String fullInput = task.goal();
        if (!contextPrompt.isEmpty()) {
            fullInput = "Context:\n" + contextPrompt + "\n\nTask: " + task.goal();
        }

        // Create a minimal workflow step for execution
        WorkflowStep step = task.plannedStep() != null ? task.plannedStep() :
                new WorkflowStep(
                        definition != null ? definition.getId() : "unknown",
                        task.goal()
                );

        // Execute using the specialist agent
        AgentExecutionResult result = executor.execute(
                definition,
                fullInput,
                "",
                step,
                fullInput
        );

        return result != null ? result.content() : "";
    }

    private String resolveContextRefs(List<String> contextRefs) {
        if (contextRefs == null || contextRefs.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (String refId : contextRefs) {
            context.resolveContextRef(refId, sharedContextStore)
                    .ifPresent(content -> sb.append("[").append(refId).append("]: ")
                            .append(content).append("\n"));
        }
        return sb.toString();
    }

    private void notifyError(AgentTask task, Throwable error) {
        lifecycleHooks.forEach(hook -> {
            try {
                hook.onError(this, task, error);
            } catch (Exception e) {
                log.warn("Lifecycle hook {} threw exception on error", hook.getClass().getSimpleName(), e);
            }
        });
    }
}
