package com.openmanus.saa.service.supervisor;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.agent.AgentRegistryService;
import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.model.AgentRun;
import com.openmanus.saa.model.AgentTask;
import com.openmanus.saa.model.AgentTaskStatus;
import com.openmanus.saa.service.agent.SpecialistAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Supervisor service for coordinating multi-agent execution.
 * Acts as the Team Lead in the multi-agent architecture.
 */
@Service
public class SupervisorAgentService {

    private static final Logger log = LoggerFactory.getLogger(SupervisorAgentService.class);

    private static final String STATE_RUN_ID = "runId";
    private static final String STATE_SESSION_ID = "sessionId";
    private static final String STATE_OBJECTIVE = "objective";
    private static final String STATE_TASKS = "tasks";
    private static final String STATE_RESULTS = "results";
    private static final String STATE_RESPONSE = "response";

    private final AgentRegistryService agentRegistryService;
    private final Map<String, SpecialistAgent> specialistAgents;
    private final OpenManusProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, AgentRun> activeRuns;
    private final Map<String, SharedTaskBoard> taskBoards;
    private final Map<String, AgentPeerRegistry> peerRegistries;
    private final List<AgentLifecycleHook> lifecycleHooks;
    private final SharedContextStore sharedContextStore;
    private volatile CompiledGraph multiAgentGraph;
    private final ExecutorService executorService;

    public SupervisorAgentService(
            AgentRegistryService agentRegistryService,
            List<SpecialistAgent> specialistAgentList,
            OpenManusProperties properties,
            Optional<List<AgentLifecycleHook>> lifecycleHooks
    ) {
        this.agentRegistryService = agentRegistryService;
        this.specialistAgents = new ConcurrentHashMap<>();
        for (SpecialistAgent agent : specialistAgentList) {
            this.specialistAgents.put(agent.name(), agent);
        }
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.activeRuns = new ConcurrentHashMap<>();
        this.taskBoards = new ConcurrentHashMap<>();
        this.peerRegistries = new ConcurrentHashMap<>();
        this.lifecycleHooks = lifecycleHooks.orElse(List.of());
        this.sharedContextStore = new SharedContextStore();
        this.executorService = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );
    }

    /**
     * Execute a multi-agent task.
     *
     * @param sessionId the session ID
     * @param objective the objective to achieve
     * @return the execution response
     */
    public MultiAgentExecutionResponse execute(String sessionId, String objective) {
        return execute(sessionId, objective, List.of());
    }

    /**
     * Execute a multi-agent task with pre-defined tasks.
     *
     * @param sessionId the session ID
     * @param objective the objective to achieve
     * @param tasks the tasks to execute
     * @return the execution response
     */
    public MultiAgentExecutionResponse execute(String sessionId, String objective, List<AgentTask> tasks) {
        if (!properties.getMultiAgent().isEnabled()) {
            return MultiAgentExecutionResponse.disabled("Multi-agent execution is not enabled");
        }

        String runId = UUID.randomUUID().toString();
        log.info("Starting multi-agent execution {} for objective: {}", runId, objective);

        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put(STATE_SESSION_ID, sessionId);
            input.put(STATE_OBJECTIVE, objective);
            input.put(STATE_RUN_ID, runId);
            input.put(STATE_TASKS, tasks);

            Optional<OverAllState> state = getOrCreateMultiAgentGraph().invoke(input);
            return extractResponse(state);
        } catch (Exception ex) {
            log.error("Multi-agent execution {} failed", runId, ex);
            return MultiAgentExecutionResponse.failure(runId, "Execution failed: " + ex.getMessage());
        }
    }

    /**
     * Execute with explicit task decomposition.
     *
     * @param sessionId the session ID
     * @param objective the objective
     * @param tasks the pre-defined tasks
     * @return the execution response
     */
    public MultiAgentExecutionResponse executeWithTasks(String sessionId, String objective, List<AgentTask> tasks) {
        return execute(sessionId, objective, tasks);
    }

    // ================== Graph Node Methods ==================

    /**
     * Node: Create AgentRun and initialize execution context.
     */
    Map<String, Object> initializeRunNode(OverAllState state) {
        String runId = state.value(STATE_RUN_ID, String.class).orElse(UUID.randomUUID().toString());
        String sessionId = state.value(STATE_SESSION_ID, String.class).orElse("default");
        String objective = state.value(STATE_OBJECTIVE, String.class).orElse("");

        AgentRun run = AgentRun.of(runId, sessionId, objective);
        activeRuns.put(runId, run.withStarted());

        SharedTaskBoard taskBoard = new SharedTaskBoard(runId);
        taskBoards.put(runId, taskBoard);

        AgentPeerRegistry peerRegistry = createPeerRegistry(runId);
        peerRegistries.put(runId, peerRegistry);

        log.info("Initialized run {} for session {}", runId, sessionId);

        return Map.of(
                STATE_RUN_ID, runId,
                "run", run
        );
    }

    /**
     * Node: Decompose objective into tasks.
     */
    Map<String, Object> decomposeObjectiveNode(OverAllState state) {
        String runId = state.value(STATE_RUN_ID, String.class).orElseThrow();
        String objective = state.value(STATE_OBJECTIVE, String.class).orElseThrow();

        @SuppressWarnings("unchecked")
        List<AgentTask> providedTasks = state.value(STATE_TASKS, List.class).orElse(List.of());

        List<AgentTask> tasks;
        if (providedTasks.isEmpty()) {
            tasks = decomposeObjective(runId, objective);
        } else {
            tasks = providedTasks;
        }

        SharedTaskBoard taskBoard = taskBoards.get(runId);
        taskBoard.addTasks(tasks);

        log.info("Decomposed objective into {} tasks for run {}", tasks.size(), runId);

        return Map.of("decomposedTasks", tasks);
    }

    /**
     * Node: Resolve task dependencies.
     */
    Map<String, Object> resolveDependenciesNode(OverAllState state) {
        String runId = state.value(STATE_RUN_ID, String.class).orElseThrow();
        SharedTaskBoard taskBoard = taskBoards.get(runId);

        List<AgentTask> sortedTasks = topologicalSort(taskBoard.getAllTasks());

        log.info("Resolved dependencies for {} tasks in run {}", sortedTasks.size(), runId);

        return Map.of("sortedTasks", sortedTasks);
    }

    /**
     * Node: Initialize agent peers.
     */
    Map<String, Object> initializePeersNode(OverAllState state) {
        String runId = state.value(STATE_RUN_ID, String.class).orElseThrow();
        AgentPeerRegistry registry = peerRegistries.get(runId);
        SharedTaskBoard taskBoard = taskBoards.get(runId);

        // Group tasks by assigned agent
        Map<String, List<AgentTask>> tasksByAgent = taskBoard.getAllTasks().stream()
                .filter(task -> task.assignedAgentId() != null)
                .collect(Collectors.groupingBy(AgentTask::assignedAgentId));

        // Create peers for each unique agent
        for (String agentId : tasksByAgent.keySet()) {
            try {
                registry.createPeer(agentId);
            } catch (Exception e) {
                log.warn("Failed to create peer for agent {}: {}", agentId, e.getMessage());
            }
        }

        // Start all peers
        registry.startAll();

        log.info("Initialized {} peers for run {}", registry.size(), runId);

        return Map.of("peersInitialized", true);
    }

    /**
     * Node: Execute tasks in parallel.
     */
    Map<String, Object> executeParallelNode(OverAllState state) {
        String runId = state.value(STATE_RUN_ID, String.class).orElseThrow();
        SharedTaskBoard taskBoard = taskBoards.get(runId);
        AgentPeerRegistry registry = peerRegistries.get(runId);

        int maxParallel = properties.getMultiAgent().getMaxParallelAgents();
        Map<String, TaskExecutionResult> results = new ConcurrentHashMap<>();

        while (!taskBoard.isAllCompleted()) {
            List<AgentTask> readyTasks = taskBoard.getReadyTasks();

            if (readyTasks.isEmpty()) {
                if (taskBoard.hasBlockedTasks()) {
                    log.warn("Run {} has blocked tasks that cannot proceed", runId);
                    break;
                }
                // Wait for running tasks to complete
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            // Limit parallel execution
            List<AgentTask> tasksToExecute = readyTasks.stream()
                    .limit(maxParallel)
                    .toList();

            // Mark tasks as started
            tasksToExecute.forEach(task -> taskBoard.markStarted(task.taskId()));

            // Execute in parallel
            List<CompletableFuture<Void>> futures = tasksToExecute.stream()
                    .map(task -> CompletableFuture.runAsync(() -> {
                        try {
                            AgentPeer peer = findPeerForTask(registry, task);
                            if (peer == null) {
                                taskBoard.markFailed(task.taskId(), "No suitable agent found");
                                return;
                            }

                            TaskExecutionResult result = peer.executeTask(task);
                            results.put(task.taskId(), result);

                            if (result.isSuccess()) {
                                taskBoard.markCompleted(task.taskId(), result.output());
                            } else {
                                taskBoard.markFailed(task.taskId(), result.error());
                            }
                        } catch (Exception e) {
                            log.error("Task {} execution failed", task.taskId(), e);
                            taskBoard.markFailed(task.taskId(), e.getMessage());
                        }
                    }, executorService))
                    .toList();

            // Wait for batch to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        log.info("Parallel execution completed for run {} with {} results", runId, results.size());

        return Map.of(STATE_RESULTS, new HashMap<>(results));
    }

    /**
     * Node: Aggregate results from all tasks.
     */
    Map<String, Object> aggregateResultsNode(OverAllState state) {
        String runId = state.value(STATE_RUN_ID, String.class).orElseThrow();
        SharedTaskBoard taskBoard = taskBoards.get(runId);

        List<TaskExecutionResult> successfulResults = new ArrayList<>();
        List<TaskExecutionResult> failedResults = new ArrayList<>();

        for (AgentTask task : taskBoard.getAllTasks()) {
            taskBoard.getTaskResult(task.taskId())
                    .map(result -> TaskExecutionResult.success(task.taskId(), task.assignedAgentId(), result, 0))
                    .ifPresent(successfulResults::add);

            taskBoard.getTaskError(task.taskId())
                    .map(error -> TaskExecutionResult.failure(task.taskId(), task.assignedAgentId(), error, 0))
                    .ifPresent(failedResults::add);
        }

        String aggregatedOutput = buildAggregatedOutput(taskBoard);

        log.info("Aggregated {} successful and {} failed results for run {}",
                successfulResults.size(), failedResults.size(), runId);

        return Map.of(
                "successfulResults", successfulResults,
                "failedResults", failedResults,
                "aggregatedOutput", aggregatedOutput
        );
    }

    /**
     * Node: Build final response.
     */
    Map<String, Object> finalizeResponseNode(OverAllState state) {
        String runId = state.value(STATE_RUN_ID, String.class).orElseThrow();
        String sessionId = state.value(STATE_SESSION_ID, String.class).orElse("default");
        String objective = state.value(STATE_OBJECTIVE, String.class).orElse("");
        SharedTaskBoard taskBoard = taskBoards.get(runId);
        String aggregatedOutput = state.value("aggregatedOutput", String.class).orElse("");

        // Stop all peers
        AgentPeerRegistry registry = peerRegistries.get(runId);
        if (registry != null) {
            registry.stopAll();
        }

        // Build status summary
        Map<AgentTaskStatus, Long> summary = taskBoard.getStatusSummary();

        MultiAgentExecutionResponse response = new MultiAgentExecutionResponse(
                runId,
                sessionId,
                objective,
                MultiAgentExecutionResponse.ExecutionStatus.COMPLETED,
                taskBoard.getAllTasks(),
                taskBoard.getTaskResults(),
                aggregatedOutput,
                summary,
                null
        );

        // Update run
        AgentRun run = activeRuns.get(runId);
        if (run != null) {
            activeRuns.put(runId, run.withCompleted());
        }

        log.info("Finalized response for run {}", runId);

        return Map.of(STATE_RESPONSE, response);
    }

    // ================== Helper Methods ==================

    private CompiledGraph getOrCreateMultiAgentGraph() {
        if (multiAgentGraph == null) {
            multiAgentGraph = buildMultiAgentGraph();
        }
        return multiAgentGraph;
    }

    private CompiledGraph buildMultiAgentGraph() {
        try {
            StateGraph graph = new StateGraph();

            graph.addNode("initializeRun", state -> CompletableFuture.completedFuture(initializeRunNode(state)));
            graph.addNode("decomposeObjective", state -> CompletableFuture.completedFuture(decomposeObjectiveNode(state)));
            graph.addNode("resolveDependencies", state -> CompletableFuture.completedFuture(resolveDependenciesNode(state)));
            graph.addNode("initializePeers", state -> CompletableFuture.completedFuture(initializePeersNode(state)));
            graph.addNode("executeParallel", state -> CompletableFuture.completedFuture(executeParallelNode(state)));
            graph.addNode("aggregateResults", state -> CompletableFuture.completedFuture(aggregateResultsNode(state)));
            graph.addNode("finalizeResponse", state -> CompletableFuture.completedFuture(finalizeResponseNode(state)));

            graph.addEdge(StateGraph.START, "initializeRun");
            graph.addEdge("initializeRun", "decomposeObjective");
            graph.addEdge("decomposeObjective", "resolveDependencies");
            graph.addEdge("resolveDependencies", "initializePeers");
            graph.addEdge("initializePeers", "executeParallel");
            graph.addEdge("executeParallel", "aggregateResults");
            graph.addEdge("aggregateResults", "finalizeResponse");
            graph.addEdge("finalizeResponse", StateGraph.END);

            return graph.compile();
        } catch (GraphStateException ex) {
            throw new IllegalStateException("Failed to build multi-agent graph", ex);
        }
    }

    private MultiAgentExecutionResponse extractResponse(Optional<OverAllState> state) {
        return state.flatMap(s -> s.value(STATE_RESPONSE, MultiAgentExecutionResponse.class))
                .orElseGet(() -> MultiAgentExecutionResponse.failure("unknown", "No response generated"));
    }

    private AgentPeerRegistry createPeerRegistry(String runId) {
        return new AgentPeerRegistry(
                agentRegistryService,
                specialistAgents,
                properties.getMultiAgent(),
                sharedContextStore,
                lifecycleHooks
        );
    }

    private List<AgentTask> decomposeObjective(String runId, String objective) {
        // Simple decomposition - in real implementation, use LLM
        List<AgentTask> tasks = new ArrayList<>();

        // Get available agents
        List<String> agentIds = agentRegistryService.listEnabled().stream()
                .map(com.openmanus.saa.agent.AgentDefinition::getId)
                .toList();

        // Create a single task for now (LLM-based decomposition would be more sophisticated)
        String taskId = "task-" + UUID.randomUUID().toString().substring(0, 8);
        String agentId = agentIds.isEmpty() ? "manus" : agentIds.get(0);

        tasks.add(AgentTask.of(taskId, objective, agentId));

        return tasks;
    }

    private List<AgentTask> topologicalSort(List<AgentTask> tasks) {
        // Simple topological sort based on dependencies
        List<AgentTask> sorted = new ArrayList<>();
        Map<String, Boolean> visited = new HashMap<>();

        for (AgentTask task : tasks) {
            visit(task, tasks, visited, sorted);
        }

        return sorted;
    }

    private void visit(AgentTask task, List<AgentTask> allTasks, Map<String, Boolean> visited, List<AgentTask> sorted) {
        if (visited.containsKey(task.taskId())) {
            return;
        }

        visited.put(task.taskId(), true);

        for (String depId : task.dependsOn()) {
            allTasks.stream()
                    .filter(t -> t.taskId().equals(depId))
                    .findFirst()
                    .ifPresent(depTask -> visit(depTask, allTasks, visited, sorted));
        }

        sorted.add(task);
    }

    private AgentPeer findPeerForTask(AgentPeerRegistry registry, AgentTask task) {
        if (task.assignedAgentId() == null) {
            return registry.getAllPeers().stream().findFirst().orElse(null);
        }

        return registry.findPeersByName(task.assignedAgentId())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private String buildAggregatedOutput(SharedTaskBoard taskBoard) {
        StringBuilder sb = new StringBuilder();

        for (AgentTask task : taskBoard.getCompletedTasks()) {
            sb.append("## Task: ").append(task.goal()).append("\n\n");
            taskBoard.getTaskResult(task.taskId())
                    .ifPresent(result -> sb.append(result).append("\n\n"));
        }

        if (taskBoard.hasFailedTasks()) {
            sb.append("## Failed Tasks:\n");
            for (AgentTask task : taskBoard.getFailedTasks()) {
                sb.append("- ").append(task.goal()).append(": ");
                taskBoard.getTaskError(task.taskId()).ifPresent(sb::append);
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Cleanup resources for a run.
     *
     * @param runId the run ID to cleanup
     */
    public void cleanupRun(String runId) {
        AgentPeerRegistry registry = peerRegistries.remove(runId);
        if (registry != null) {
            registry.clear();
        }
        taskBoards.remove(runId);
        activeRuns.remove(runId);
        log.debug("Cleaned up run {}", runId);
    }
}
