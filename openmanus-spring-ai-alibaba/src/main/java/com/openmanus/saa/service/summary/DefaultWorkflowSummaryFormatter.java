package com.openmanus.saa.service.summary;

import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.WorkflowExecutionStatus;
import com.openmanus.saa.model.WorkflowStep;
import com.openmanus.saa.util.ResponseLanguageHelper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultWorkflowSummaryFormatter implements WorkflowSummaryFormatter {

    private static final Logger log = LoggerFactory.getLogger(DefaultWorkflowSummaryFormatter.class);

    private final ChatClient chatClient;

    public DefaultWorkflowSummaryFormatter(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String format(WorkflowSummaryContext context) {
        if (context == null) {
            return "";
        }

        String fallback = fallbackMessage(context);
        try {
            String languageDirective = ResponseLanguageHelper.responseDirective(context.objective());
            String prompt = """
                    You rewrite workflow status messages for end users.

                    Requirements:
                    - Keep the message concise and natural.
                    - Use only the facts provided. Do not invent capabilities, files, or recovery actions.
                    - Make the message user-friendly rather than technical.
                    - If the workflow failed because of missing capability, clearly say what capability is missing and give a practical downgrade or next-step suggestion when one is already implied by the facts.
                    - If the workflow is waiting for user feedback, clearly state what is blocked and what the user can do next.
                    - Preserve the original meaning and status.
                    - Return plain text only, no markdown code fences.

                    %s
                    """.formatted(languageDirective);

            String content = chatClient.prompt()
                    .system(prompt)
                    .user(buildUserPrompt(context, fallback))
                    .call()
                    .content();
            if (content == null || content.isBlank()) {
                return fallback;
            }
            return content.trim();
        } catch (Exception ex) {
            log.warn("Failed to format workflow summary with LLM, using fallback message.", ex);
            return fallback;
        }
    }

    private String buildUserPrompt(WorkflowSummaryContext context, String fallback) {
        WorkflowStep failedStep = context.failedStep();
        HumanFeedbackRequest pendingFeedback = context.pendingFeedback();
        String failureReason = failedStep == null ? null : failedStep.getErrorMessage();
        String blockedStep = context.currentStep() == null || context.currentStep().isBlank()
                ? "none"
                : context.currentStep();
        return """
                Workflow facts:
                - objective: %s
                - status: %s
                - currentStep: %s
                - failedStep: %s
                - failureReason: %s
                - pendingFeedback: %s
                - artifacts: %s
                - executionLogCount: %d

                Draft message:
                %s

                Rewrite the draft message for the user.
                """.formatted(
                safe(context.objective()),
                context.status(),
                blockedStep,
                failedStep == null ? "none" : failedStep.getDescription(),
                safe(failureReason),
                pendingFeedback == null ? "no" : "yes",
                context.artifacts().isEmpty() ? "none" : String.join(", ", context.artifacts()),
                context.executionLog().size(),
                fallback
        );
    }

    private String fallbackMessage(WorkflowSummaryContext context) {
        WorkflowExecutionStatus status = context.status();
        if (status == WorkflowExecutionStatus.NEEDS_HUMAN_INTERVENTION) {
            return pausedFallback(context);
        }
        if (status == WorkflowExecutionStatus.FAILED) {
            return failedFallback(context);
        }
        if (status == WorkflowExecutionStatus.ABORTED) {
            return abortedFallback(context);
        }
        return completedFallback(context);
    }

    private String completedFallback(WorkflowSummaryContext context) {
        if (context.artifacts() != null && !context.artifacts().isEmpty()) {
            return ResponseLanguageHelper.choose(
                    context.objective(),
                    "工作流已完成，已生成以下产物：\n- " + String.join("\n- ", context.artifacts()),
                    "Workflow completed. Generated artifacts:\n- " + String.join("\n- ", context.artifacts())
            );
        }
        String baseMessage = context.baseMessage();
        if (baseMessage != null && !baseMessage.isBlank()) {
            return baseMessage;
        }
        return ResponseLanguageHelper.choose(
                context.objective(),
                "工作流已完成。",
                "Workflow completed."
        );
    }

    private String abortedFallback(WorkflowSummaryContext context) {
        WorkflowStep failedStep = context.failedStep();
        return ResponseLanguageHelper.choose(
                context.objective(),
                String.format(
                        "工作流已根据用户反馈终止。%n%n当前步骤: %s%n原因: %s",
                        failedStep == null ? "未知" : failedStep.getDescription(),
                        failedStep == null ? "未提供" : safe(failedStep.getErrorMessage())
                ),
                String.format(
                        "Workflow aborted after user feedback.%n%nCurrent step: %s%nReason: %s",
                        failedStep == null ? "unknown" : failedStep.getDescription(),
                        failedStep == null ? "not provided" : safe(failedStep.getErrorMessage())
                )
        );
    }

    private String pausedFallback(WorkflowSummaryContext context) {
        HumanFeedbackRequest pendingFeedback = context.pendingFeedback();
        WorkflowStep failedStep = context.failedStep();
        long completedSteps = context.executedSteps().stream().filter(WorkflowStep::isCompleted).count();
        String suggestedAction = pendingFeedback == null ? null : pendingFeedback.getSuggestedAction();
        return ResponseLanguageHelper.choose(
                context.objective(),
                "工作流已暂停，正在等待用户反馈。"
                        + String.format("%n%n已完成步骤: %d / %d", completedSteps, context.executedSteps().size())
                        + String.format("%n阻塞步骤: %s", failedStep == null ? "未知" : failedStep.getDescription())
                        + String.format("%n原因: %s", failedStep == null ? "未提供" : safe(failedStep.getErrorMessage()))
                        + (suggestedAction == null || suggestedAction.isBlank() ? "" : String.format("%n建议操作: %s", suggestedAction)),
                "Workflow paused and waiting for user feedback."
                        + String.format("%n%nCompleted steps: %d / %d", completedSteps, context.executedSteps().size())
                        + String.format("%nBlocked step: %s", failedStep == null ? "unknown" : failedStep.getDescription())
                        + String.format("%nReason: %s", failedStep == null ? "not provided" : safe(failedStep.getErrorMessage()))
                        + (suggestedAction == null || suggestedAction.isBlank() ? "" : String.format("%nSuggested action: %s", suggestedAction))
        );
    }

    private String failedFallback(WorkflowSummaryContext context) {
        WorkflowStep failedStep = context.failedStep();
        return ResponseLanguageHelper.choose(
                context.objective(),
                String.format(
                        "工作流执行失败。%n%n失败步骤: %s%n原因: %s",
                        failedStep == null ? "未知" : failedStep.getDescription(),
                        failedStep == null ? "未提供" : safe(failedStep.getErrorMessage())
                ),
                String.format(
                        "Workflow execution failed.%n%nFailed step: %s%nReason: %s",
                        failedStep == null ? "unknown" : failedStep.getDescription(),
                        failedStep == null ? "not provided" : safe(failedStep.getErrorMessage())
                )
        );
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "none" : value.trim();
    }
}
