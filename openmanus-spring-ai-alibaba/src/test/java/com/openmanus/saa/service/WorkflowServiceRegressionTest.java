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
import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.IntentRouteMode;
import com.openmanus.saa.model.OutputEvaluationStatus;
import com.openmanus.saa.model.PlanEvaluationResult;
import com.openmanus.saa.model.ResponseMode;
import com.openmanus.saa.model.StepStatus;
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

    // TODO: retryOutputWorkflowNode method was removed - test needs rewrite
    // @Test
    // void retryOutputWorkflowNodeExecutesFromClampedRetryIndex() { ... }

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
                mock(com.openmanus.saa.service.supervisor.SupervisorAgentService.class),
                mock(WorkflowCheckpointService.class),
                mock(com.openmanus.saa.service.context.ConversationContextFactory.class)
        );
    }

    // ===== evaluatePlan tests =====

    @Test
    void evaluatePlanReturnsOkForSingleStep() {
        SkillCapabilityService skillCapabilityService = mock(SkillCapabilityService.class);
        WorkflowService service = newWorkflowService(skillCapabilityService);
        List<WorkflowStep> steps = List.of(
                new WorkflowStep("manus", "search for Nanjing attractions")
        );

        PlanEvaluationResult result = ReflectionTestUtils.invokeMethod(
                service,
                "evaluatePlan",
                "plan a 7-day Nanjing trip",
                steps,
                (com.openmanus.saa.model.IntentResolution) null
        );

        assertThat(result.needsRevision()).isFalse();
    }

    @Test
    void evaluatePlanReturnsOkForArtifactOutput() {
        SkillCapabilityService skillCapabilityService = mock(SkillCapabilityService.class);
        WorkflowService service = newWorkflowService(skillCapabilityService);
        IntentResolution needsFile = new IntentResolution(
                "export",
                1.0,
                IntentRouteMode.PLAN_EXECUTE,
                null,
                Map.of("outputExpectation", Map.of("needsFile", true)),
                List.of()
        );
        List<WorkflowStep> steps = List.of(
                new WorkflowStep("manus", "read skill docx-format"),
                new WorkflowStep("manus", "export itinerary to docx")
        );

        PlanEvaluationResult result = ReflectionTestUtils.invokeMethod(
                service,
                "evaluatePlan",
                "export itinerary to docx",
                steps,
                needsFile
        );

        assertThat(result.needsRevision()).isFalse();
    }

    @Test
    void evaluatePlanNeedsRevisionForMultiStepCollectionWithoutIntegration() {
        SkillCapabilityService skillCapabilityService = mock(SkillCapabilityService.class);
        WorkflowService service = newWorkflowService(skillCapabilityService);
        List<WorkflowStep> steps = List.of(
                new WorkflowStep("manus", "search for Nanjing attractions"),
                new WorkflowStep("manus", "retrieve weather for Nanjing")
        );

        PlanEvaluationResult result = ReflectionTestUtils.invokeMethod(
                service,
                "evaluatePlan",
                "plan a 7-day Nanjing trip",
                steps,
                (IntentResolution) null
        );

        assertThat(result.needsRevision()).isTrue();
        assertThat(result.revisionSuggestion()).isNotNull();
        assertThat(result.missingElements()).contains("缺少最终整合步骤");
    }

    @Test
    void evaluatePlanReturnsOkForMultiStepWithIntegration() {
        SkillCapabilityService skillCapabilityService = mock(SkillCapabilityService.class);
        WorkflowService service = newWorkflowService(skillCapabilityService);
        List<WorkflowStep> steps = List.of(
                new WorkflowStep("manus", "search for Nanjing attractions"),
                new WorkflowStep("manus", "retrieve weather for Nanjing"),
                new WorkflowStep("manus", "compose the final itinerary")
        );

        PlanEvaluationResult result = ReflectionTestUtils.invokeMethod(
                service,
                "evaluatePlan",
                "plan a 7-day Nanjing trip",
                steps,
                (IntentResolution) null
        );

        assertThat(result.needsRevision()).isFalse();
    }

    // ===== SKIPPED step handling tests =====

    @Test
    void generateDeliverableRoutesToSkippedStepSynthesisWhenHasSkippedSteps() {
        SkillCapabilityService skillCapabilityService = mock(SkillCapabilityService.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        lenient().when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callSpec);
        lenient().when(callSpec.content()).thenReturn("Generated content with completion note");

        WorkflowService service = newWorkflowServiceWithChatClient(skillCapabilityService, chatClient);

        WorkflowStep skippedStep = new WorkflowStep("manus", "search for Java resources")
                .withStatus(StepStatus.SKIPPED);

        WorkflowStep completedStep = new WorkflowStep("manus", "generate learning plan")
                .withStatus(StepStatus.COMPLETED)
                .withResult("Plan created successfully");

        List<WorkflowStep> steps = List.of(skippedStep, completedStep);

        String result = ReflectionTestUtils.invokeMethod(
                service,
                "generateDeliverable",
                "帮我规划 Java 学习路线",
                steps,
                List.<String>of(),
                ResponseMode.FINAL_DELIVERABLE
        );

        assertThat(result).isEqualTo("Generated content with completion note");
        verify(chatClient).prompt();
    }

    @Test
    void generateDeliverableUsesExistingLogicWhenNoSkippedSteps() {
        SkillCapabilityService skillCapabilityService = mock(SkillCapabilityService.class);
        ChatClient chatClient = mock(ChatClient.class);

        WorkflowService service = newWorkflowServiceWithChatClient(skillCapabilityService, chatClient);

        WorkflowStep completedStep = new WorkflowStep("manus", "generate learning plan")
                .withStatus(StepStatus.COMPLETED)
                .withResult("Complete learning plan content");

        List<WorkflowStep> steps = List.of(completedStep);

        String result = ReflectionTestUtils.invokeMethod(
                service,
                "generateDeliverable",
                "帮我规划 Java 学习路线",
                steps,
                List.<String>of(),
                ResponseMode.FINAL_DELIVERABLE
        );

        assertThat(result).isEqualTo("Complete learning plan content");
        verify(chatClient, never()).prompt();
    }

    private WorkflowService newWorkflowServiceWithChatClient(
            SkillCapabilityService skillCapabilityService,
            ChatClient chatClient
    ) {
        OpenManusProperties properties = new OpenManusProperties();
        properties.setWorkspace("./workspace");

        SessionStorage sessionStorage = mock(SessionStorage.class);
        lenient().when(sessionStorage.findById(any())).thenReturn(Optional.empty());
        lenient().when(sessionStorage.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        return new WorkflowService(
                chatClient,
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
                mock(com.openmanus.saa.service.supervisor.SupervisorAgentService.class),
                mock(WorkflowCheckpointService.class),
                mock(com.openmanus.saa.service.context.ConversationContextFactory.class)
        );
    }
}
