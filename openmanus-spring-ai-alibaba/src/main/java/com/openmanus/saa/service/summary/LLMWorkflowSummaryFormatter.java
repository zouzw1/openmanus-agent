package com.openmanus.saa.service.summary;

import com.openmanus.saa.model.StepStatus;
import com.openmanus.saa.model.WorkflowStep;
import com.openmanus.saa.util.ResponseLanguageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LLMWorkflowSummaryFormatter implements WorkflowSummaryFormatter {

    private static final Logger log = LoggerFactory.getLogger(LLMWorkflowSummaryFormatter.class);

    private final ChatClient chatClient;

    public LLMWorkflowSummaryFormatter(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public boolean supports(WorkflowSummaryContext context) {
        return hasDeliverableContent(context);
    }

    @Override
    public String format(WorkflowSummaryContext context) {
        String deliverableData = extractDeliverableData(context);
        if (deliverableData == null || deliverableData.isBlank()) {
            return null;
        }

        String statusInfo = buildStatusInfo(context);

        String prompt = buildPrompt(context.objective(), deliverableData, statusInfo);

        try {
            String content = chatClient.prompt()
                    .system("You are a helpful assistant that summarizes workflow results for users. " +
                            "Output natural, user-friendly content without meta-commentary. " +
                            "Focus on the actual deliverable content the user requested.")
                    .user(prompt)
                    .call()
                    .content();
            return content != null ? content.trim() : null;
        } catch (Exception ex) {
            log.warn("Failed to generate LLM summary, fallback to next formatter", ex);
            return null;
        }
    }

    private boolean hasDeliverableContent(WorkflowSummaryContext context) {
        if (context.executedSteps() == null || context.executedSteps().isEmpty()) {
            return false;
        }
        return context.executedSteps().stream()
                .filter(s -> s.getStatus() == StepStatus.COMPLETED)
                .anyMatch(s -> s.getToolOutputs() != null && !s.getToolOutputs().isEmpty());
    }

    private String extractDeliverableData(WorkflowSummaryContext context) {
        StringBuilder sb = new StringBuilder();
        for (WorkflowStep step : context.executedSteps()) {
            if (step.getStatus() != StepStatus.COMPLETED) continue;
            if (step.getToolOutputs() == null || step.getToolOutputs().isEmpty()) continue;
            for (String output : step.getToolOutputs()) {
                String data = extractToolOutput(output);
                if (data != null && !data.isBlank()) {
                    sb.append(data).append("\n\n");
                }
            }
        }
        return sb.toString().trim();
    }

    private String extractToolOutput(String toolOutput) {
        if (toolOutput == null) return "";
        int pipeIndex = toolOutput.indexOf('|');
        if (pipeIndex >= 0 && pipeIndex < toolOutput.length() - 1) {
            return toolOutput.substring(pipeIndex + 1).trim();
        }
        return toolOutput.trim();
    }

    private String buildStatusInfo(WorkflowSummaryContext context) {
        List<WorkflowStep> skipped = context.executedSteps().stream()
                .filter(s -> s.getStatus() == StepStatus.SKIPPED)
                .toList();
        List<WorkflowStep> failed = context.executedSteps().stream()
                .filter(s -> s.getStatus() == StepStatus.FAILED
                        || s.getStatus() == StepStatus.FAILED_NEEDS_HUMAN_INTERVENTION)
                .toList();

        StringBuilder sb = new StringBuilder();
        if (!skipped.isEmpty()) {
            sb.append("已跳过步骤：");
            sb.append(skipped.stream()
                    .map(WorkflowStep::getDescription)
                    .collect(Collectors.joining("; ")));
        }
        if (!failed.isEmpty()) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append("失败步骤：");
            sb.append(failed.stream()
                    .map(s -> s.getDescription() + "（" + safe(s.getErrorMessage()) + "）")
                    .collect(Collectors.joining("; ")));
        }
        return sb.isEmpty() ? "无" : sb.toString();
    }

    private String buildPrompt(String objective, String deliverableData, String statusInfo) {
        return ResponseLanguageHelper.choose(objective,
                """
                ## 用户请求
                %s

                ## 生成的交付内容
                %s

                ## 执行情况
                %s

                请生成简洁的自然语言 summary：
                1. 直接展示核心交付内容（学习路线图/行程规划等）
                2. 如果有跳过/失败步骤，简述原因（1-2句话）
                3. 不要添加"下一步建议"或"推荐后续操作"
                4. 使用中文
                5. 输出可以直接复制使用的完整内容
                """,
                """
                ## User Request
                %s

                ## Generated Deliverable
                %s

                ## Execution Status
                %s

                Generate a concise natural language summary:
                1. Directly show the core deliverable content
                2. If there are skipped/failed steps, briefly explain (1-2 sentences)
                3. Do NOT add "next step suggestions" or "recommended follow-up"
                4. Use the user's language
                5. Output complete content ready to use
                """
        ).formatted(objective, deliverableData, statusInfo);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "未知" : value.trim();
    }
}
