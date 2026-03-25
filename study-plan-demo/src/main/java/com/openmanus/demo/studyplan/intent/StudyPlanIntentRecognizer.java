package com.openmanus.demo.studyplan.intent;

import com.openmanus.saa.model.IntentRouteMode;
import com.openmanus.saa.service.intent.AbstractIntentTreeRecognizer;
import com.openmanus.saa.service.intent.IntentDecision;
import com.openmanus.saa.service.intent.IntentTreeNode;
import com.openmanus.saa.service.intent.SimpleIntentTreeNode;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class StudyPlanIntentRecognizer extends AbstractIntentTreeRecognizer {

    private static final String STUDY_PLANNER_AGENT_ID = "study-planner";
    private static final String PREFER_STUDY_AGENT_HINT =
            "Prefer the study-planner agent when it has the required capabilities.";
    private static final String PREFER_STUDY_TOOLS_HINT =
            "Prefer custom learning-plan local tools before generic drafting.";
    private static final String EXPORT_HINT =
            "If the user requests a file deliverable, first generate the study-plan draft and then export it.";

    private final IntentTreeNode root = buildTree();

    @Override
    protected IntentTreeNode root() {
        return root;
    }

    private IntentTreeNode buildTree() {
        IntentTreeNode studyPlanRequest = SimpleIntentTreeNode.builder("study_plan_request")
                .matcher((prompt, session) -> isPlanLikeRequest(normalize(prompt)) || isDeliverableRequest(normalize(prompt)))
                .decision((prompt, session) -> IntentDecision.builder()
                        .routeMode(IntentRouteMode.PLAN_EXECUTE)
                        .build())
                .children(
                        SimpleIntentTreeNode.builder("study_plan_export")
                                .matcher((prompt, session) -> isDeliverableRequest(normalize(prompt)))
                                .decision((prompt, session) -> IntentDecision.builder()
                                        .intentId("study_plan_export")
                                        .confidence(0.95d)
                                        .attribute("deliverableRequested", true)
                                        .attribute("planRequested", isPlanLikeRequest(normalize(prompt)))
                                        .planningHint(PREFER_STUDY_TOOLS_HINT)
                                        .planningHint(EXPORT_HINT)
                                        .build())
                                .build(),
                        SimpleIntentTreeNode.builder("study_plan_generation")
                                .matcher((prompt, session) -> true)
                                .decision(IntentDecision.builder()
                                        .intentId("study_plan_generation")
                                        .confidence(0.95d)
                                        .attribute("deliverableRequested", false)
                                        .attribute("planRequested", true)
                                        .planningHint(PREFER_STUDY_TOOLS_HINT)
                                        .build())
                                .build()
                )
                .build();

        IntentTreeNode studyConsult = SimpleIntentTreeNode.builder("study_consult")
                .matcher((prompt, session) -> true)
                .decision(IntentDecision.builder()
                        .intentId("study_plan_generation")
                        .confidence(0.95d)
                        .routeMode(IntentRouteMode.DIRECT_CHAT)
                        .attribute("deliverableRequested", false)
                        .attribute("planRequested", false)
                        .build())
                .build();

        return SimpleIntentTreeNode.builder("study_root")
                .matcher((prompt, session) -> looksLikeStudyRequest(normalize(prompt)))
                .decision(IntentDecision.builder()
                        .preferredAgentId(STUDY_PLANNER_AGENT_ID)
                        .attribute("domain", "study")
                        .planningHint(PREFER_STUDY_AGENT_HINT)
                        .build())
                .children(studyPlanRequest, studyConsult)
                .build();
    }

    private boolean looksLikeStudyRequest(String normalized) {
        return mentionsAny(
                normalized,
                "\u5b66\u4e60",
                "\u5907\u8003",
                "\u590d\u4e60",
                "\u8bfe\u7a0b",
                "\u5237\u9898",
                "study",
                "learn",
                "exam",
                "ielts",
                "toefl",
                "spring boot",
                "java"
        );
    }

    private boolean isPlanLikeRequest(String normalized) {
        return mentionsAny(
                normalized,
                "\u5b66\u4e60\u8ba1\u5212",
                "\u5907\u8003\u8ba1\u5212",
                "\u5237\u9898\u8ba1\u5212",
                "\u5b66\u4e60\u8def\u7ebf",
                "\u590d\u4e60\u8ba1\u5212",
                "roadmap",
                "study plan",
                "learning plan"
        );
    }

    private boolean isDeliverableRequest(String normalized) {
        return mentionsAny(
                normalized,
                "\u5bfc\u51fa",
                "\u4fdd\u5b58",
                "\u6587\u6863",
                "\u6587\u4ef6",
                "word",
                "pdf",
                "markdown",
                "docx"
        );
    }

    private String normalize(String prompt) {
        return prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
    }

    private boolean mentionsAny(String normalized, String... keywords) {
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
