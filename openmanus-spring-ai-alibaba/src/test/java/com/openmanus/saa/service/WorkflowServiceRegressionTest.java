package com.openmanus.saa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.openmanus.saa.agent.AgentDefinition;
import com.openmanus.saa.agent.CapabilityAccessMode;
import com.openmanus.saa.agent.IdAccessPolicy;
import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.config.SessionConfig;
import com.openmanus.saa.model.OutputEvaluationStatus;
import com.openmanus.saa.model.ResponseMode;
import com.openmanus.saa.model.SkillCapabilityDescriptor;
import com.openmanus.saa.model.WorkflowStep;
import com.openmanus.saa.service.agent.SpecialistAgent;
import com.openmanus.saa.service.intent.IntentResolutionService;
import com.openmanus.saa.service.session.SessionCompactor;
import com.openmanus.saa.service.session.SessionManager;
import com.openmanus.saa.service.session.SessionMemoryService;
import com.openmanus.saa.service.session.storage.SessionStorage;
import com.openmanus.saa.service.summary.WorkflowSummaryFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

class WorkflowServiceRegressionTest {

    @Test
    void maybeAugmentStepWithLazyHelperToolsAcceptsNormalizedToolIds() {
        SkillCapabilityService skillCapabilityService = mock(SkillCapabilityService.class);
        when(skillCapabilityService.getCapability("docx-format")).thenReturn(Optional.of(
                new SkillCapabilityDescriptor(
                        "docx-format",
                        List.of(),
                        List.of("export"),
                        List.of("md"),
                        List.of("docx"),
                        List.of("runPowerShell", "readWorkspaceFile", "writeWorkspaceFile", "listWorkspaceFiles"),
                        "export markdown to docx"
                )
        ));

        WorkflowService service = newWorkflowService(skillCapabilityService);
        AgentDefinition agentDefinition = new AgentDefinition(
                "manus",
                "manus",
                true,
                "general",
                "general agent",
                true,
                0,
                "",
                null,
                new IdAccessPolicy(
                        CapabilityAccessMode.ALLOW_LIST,
                        Set.of("runPowerShell", "readWorkspaceFile", "writeWorkspaceFile", "listWorkspaceFiles")
                ),
                null,
                new IdAccessPolicy(CapabilityAccessMode.ALLOW_LIST, Set.of("docx-format"))
        );
        WorkflowStep step = new WorkflowStep(
                "manus",
                "export itinerary to docx",
                List.of("read_skill", "writeWorkspaceFile"),
                Map.of("skillName", "docx-format")
        );

        WorkflowStep augmented = ReflectionTestUtils.invokeMethod(
                service,
                "maybeAugmentStepWithLazyHelperTools",
                step,
                agentDefinition,
                List.of(step),
                0
        );

        assertThat(service.agentAllowsLocalTool(agentDefinition, "runPowerShell")).isTrue();
        assertThat(augmented).isNotNull();
        assertThat(augmented.getRequiredTools())
                .contains("read_skill", "writeWorkspaceFile", "runPowerShell", "readWorkspaceFile", "listWorkspaceFiles");
        assertThat(augmented.getParameterContext())
                .containsEntry("skillName", "docx-format");
        @SuppressWarnings("unchecked")
        List<String> lazyLoadedHelperTools = (List<String>) augmented.getParameterContext().get("lazyLoadedHelperTools");
        assertThat(lazyLoadedHelperTools)
                .containsExactly("runPowerShell", "readWorkspaceFile", "listWorkspaceFiles");
    }

    @Test
    void retryOutputWorkflowNodeExecutesFromClampedRetryIndex() {
        WorkflowService service = mock(WorkflowService.class);

        // Mock SessionStorage
        SessionStorage sessionStorage = mock(SessionStorage.class);
        when(sessionStorage.findById(any())).thenReturn(Optional.empty());
        when(sessionStorage.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SessionMemoryService sessionMemoryService = new SessionMemoryService(
            sessionStorage,
            mock(SessionCompactor.class),
            new SessionConfig()
        );
        WorkflowLifecycleNodeHandler handler = new WorkflowLifecycleNodeHandler(service);
        List<WorkflowStep> currentSteps = List.of(
                new WorkflowStep("manus", "step 1"),
                new WorkflowStep("manus", "step 2"),
                new WorkflowStep("manus", "step 3"),
                new WorkflowStep("manus", "step 4")
        );
        List<WorkflowStep> retriedSteps = List.of(
                currentSteps.get(0),
                currentSteps.get(1),
                currentSteps.get(2),
                new WorkflowStep("manus", "step 4 retried")
        );
        WorkflowService.OutputEvaluationDecision decision = new WorkflowService.OutputEvaluationDecision(
                OutputEvaluationStatus.BLOCKER,
                "missing docx",
                List.of("docx file was not generated"),
                "retry the last step",
                10
        );

        when(service.coerceWorkflowSteps(any())).thenReturn(currentSteps);
        when(service.sessionMemoryService()).thenReturn(sessionMemoryService);
        when(service.clampRetryStartIndex(currentSteps, 10)).thenReturn(3);
        when(service.prepareStepsForOutputRetry(currentSteps, decision, 1)).thenReturn(retriedSteps);
        when(service.executeStepsWithStatusTracking("session-1", "plan-1", retriedSteps, "build docx", 3))
                .thenReturn(retriedSteps);
        when(service.findFailedStep(retriedSteps)).thenReturn(Optional.empty());

        OverAllState state = new OverAllState(Map.of(
                "sessionId", "session-1",
                "planId", "plan-1",
                "objective", "build docx",
                "responseMode", ResponseMode.WORKFLOW_SUMMARY,
                "outputEvaluationDecision", decision,
                "outputEvaluationRetryCount", 0,
                "workflowSteps", currentSteps
        ));

        Map<String, Object> result = handler.retryOutputWorkflowNode(state);

        verify(service).executeStepsWithStatusTracking("session-1", "plan-1", retriedSteps, "build docx", 3);
        verify(service, never()).executeStepsWithStatusTracking("session-1", "plan-1", retriedSteps, "build docx", 10);
        assertThat(result).containsEntry("workflowSteps", retriedSteps);
        assertThat(result).containsEntry("outputEvaluationRetryCount", 1);
    }

    @Test
    void executionResultRecoveryExhaustedIsTrueOnFinalFailure() {
        WorkflowService.ExecutionResult result = new WorkflowService.ExecutionResult(
                false, null,
                List.of("searchTool"), List.of(),
                List.of(), List.of(),
                false, "Service unavailable",
                2, true
        );

        assertThat(result.recoveryExhausted).isTrue();
        assertThat(result.needsHumanFeedback).isFalse();
        assertThat(result.success).isFalse();
    }

    @Test
    void executionResultRecoveryExhaustedIsFalseOnSuccess() {
        WorkflowService.ExecutionResult result = new WorkflowService.ExecutionResult(
                true, "Step completed",
                List.of(), List.of(),
                List.of(), List.of(),
                false, null,
                1, false
        );

        assertThat(result.recoveryExhausted).isFalse();
        assertThat(result.success).isTrue();
    }

    @Test
    void executionResultRecoveryExhaustedIsFalseWhenNeedsHumanFeedback() {
        WorkflowService.ExecutionResult result = new WorkflowService.ExecutionResult(
                false, null,
                List.of(), List.of(),
                List.of(), List.of(),
                true, "Missing user input",
                1, false
        );

        assertThat(result.needsHumanFeedback).isTrue();
        assertThat(result.recoveryExhausted).isFalse();
    }

    private WorkflowService newWorkflowService(SkillCapabilityService skillCapabilityService) {
        OpenManusProperties properties = new OpenManusProperties();
        properties.setWorkspace("./workspace");

        // Mock SessionStorage
        SessionStorage sessionStorage = mock(SessionStorage.class);
        lenient().when(sessionStorage.findById(any())).thenReturn(Optional.empty());
        lenient().when(sessionStorage.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        return new WorkflowService(
                mock(ChatClient.class),
                mock(PlanningService.class),
                mock(com.openmanus.saa.tool.PlanningTools.class),
                properties,
                List.<SpecialistAgent>of(),
                new SessionMemoryService(
                    sessionStorage,
                    mock(SessionCompactor.class),
                    new SessionConfig()
                ),
                mock(SessionManager.class),
                mock(com.openmanus.saa.agent.AgentRegistryService.class),
                mock(SkillsService.class),
                skillCapabilityService,
                mock(AgentCapabilitySnapshotService.class),
                mock(IntentResolutionService.class),
                List.<WorkflowSummaryFormatter>of(),
                mock(com.openmanus.saa.service.supervisor.SupervisorAgentService.class)
        );
    }
}
