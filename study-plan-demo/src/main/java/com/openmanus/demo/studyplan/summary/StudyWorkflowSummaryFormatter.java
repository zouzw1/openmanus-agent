package com.openmanus.demo.studyplan.summary;

import com.openmanus.saa.model.WorkflowExecutionStatus;
import com.openmanus.saa.model.WorkflowStep;
import com.openmanus.saa.service.summary.WorkflowSummaryContext;
import com.openmanus.saa.service.summary.WorkflowSummaryFormatter;
import com.openmanus.saa.util.ResponseLanguageHelper;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class StudyWorkflowSummaryFormatter implements WorkflowSummaryFormatter {

    private static final String STUDY_AGENT_ID = "study-planner";

    @Override
    public boolean supports(WorkflowSummaryContext context) {
        if (context == null || context.objective() == null) {
            return false;
        }
        String normalized = context.objective().toLowerCase(Locale.ROOT);
        if (mentionsStudyIntent(normalized)) {
            return true;
        }
        return context.executedSteps().stream()
                .map(WorkflowStep::getAgent)
                .anyMatch(STUDY_AGENT_ID::equals);
    }

    @Override
    public String format(WorkflowSummaryContext context) {
        if (context.status() == WorkflowExecutionStatus.FAILED && context.failedStep() != null) {
            String error = context.failedStep().getErrorMessage();
            if (error != null && error.toLowerCase(Locale.ROOT).contains("word (.docx)")) {
                return ResponseLanguageHelper.choose(
                        context.objective(),
                        "当前学习计划流程已经识别到你需要 Word 文档，但这个示例环境还没有启用 Word 导出能力，因此暂时无法继续生成 .docx 文件。\n\n你可以改成输出 Markdown/纯文本版本，或者先启用 docx 导出能力后再重试。",
                        "The study-plan flow recognized that you want a Word document, but this demo environment does not have Word export enabled yet, so it cannot continue to generate a .docx file.\n\nYou can switch to Markdown/plain text output, or enable docx export first and try again."
                );
            }
        }

        if (context.status() == WorkflowExecutionStatus.COMPLETED && !context.artifacts().isEmpty()) {
            return ResponseLanguageHelper.choose(
                    context.objective(),
                    "学习计划已生成完成，可查看以下产物：\n- " + String.join("\n- ", context.artifacts()),
                    "The study plan is ready. Generated artifacts:\n- " + String.join("\n- ", context.artifacts())
            );
        }

        return context.baseMessage();
    }

    private boolean mentionsStudyIntent(String normalized) {
        return containsAny(
                normalized,
                "学习",
                "备考",
                "复习",
                "课程",
                "study",
                "learn",
                "roadmap",
                "agent学习计划",
                "study plan"
        );
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
