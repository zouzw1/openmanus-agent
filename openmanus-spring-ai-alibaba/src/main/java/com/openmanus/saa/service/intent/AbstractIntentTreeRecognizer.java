package com.openmanus.saa.service.intent;

import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.IntentRouteMode;
import com.openmanus.saa.model.session.Session;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractIntentTreeRecognizer implements IntentRecognizer {

    @Override
    public Optional<IntentResolution> recognize(String prompt, Session session) {
        IntentTreeNode root = root();
        if (root == null) {
            return Optional.empty();
        }
        return resolveNode(root, prompt, session, IntentDecision.empty())
                .flatMap(this::toResolution);
    }

    protected abstract IntentTreeNode root();

    private Optional<IntentDecision> resolveNode(
            IntentTreeNode node,
            String prompt,
            Session session,
            IntentDecision inheritedDecision
    ) {
        if (!node.matches(prompt, session)) {
            return Optional.empty();
        }

        IntentDecision mergedDecision = merge(inheritedDecision, node.decision(prompt, session), node.id());
        for (IntentTreeNode child : node.children()) {
            Optional<IntentDecision> childMatch = resolveNode(child, prompt, session, mergedDecision);
            if (childMatch.isPresent()) {
                return childMatch;
            }
        }

        return mergedDecision.isEmpty() ? Optional.empty() : Optional.of(mergedDecision);
    }

    private Optional<IntentResolution> toResolution(IntentDecision decision) {
        IntentRouteMode routeMode = decision.routeMode();
        if (routeMode == null) {
            return Optional.empty();
        }
        return Optional.of(new IntentResolution(
                decision.intentId(),
                decision.confidence() == null ? 1.0d : decision.confidence(),
                routeMode,
                decision.preferredAgentId(),
                decision.attributes(),
                decision.planningHints()
        ));
    }

    private IntentDecision merge(IntentDecision inheritedDecision, IntentDecision nodeDecision, String nodeId) {
        IntentDecision safeInherited = inheritedDecision == null ? IntentDecision.empty() : inheritedDecision;
        IntentDecision safeNodeDecision = nodeDecision == null ? IntentDecision.empty() : nodeDecision;

        String mergedIntentId = firstNonBlank(safeNodeDecision.intentId(), safeInherited.intentId());
        if (mergedIntentId == null && !safeNodeDecision.isEmpty()) {
            mergedIntentId = nodeId;
        }

        Double mergedConfidence = safeNodeDecision.confidence() != null
                ? safeNodeDecision.confidence()
                : safeInherited.confidence();
        IntentRouteMode mergedRouteMode = safeNodeDecision.routeMode() != null
                ? safeNodeDecision.routeMode()
                : safeInherited.routeMode();
        String mergedPreferredAgentId = firstNonBlank(safeNodeDecision.preferredAgentId(), safeInherited.preferredAgentId());

        Map<String, Object> mergedAttributes = new LinkedHashMap<>();
        mergedAttributes.putAll(safeInherited.attributes());
        mergedAttributes.putAll(safeNodeDecision.attributes());

        List<String> mergedPlanningHints = new ArrayList<>(
                new LinkedHashSet<>(safeInherited.planningHints())
        );
        for (String planningHint : safeNodeDecision.planningHints()) {
            if (planningHint != null && !planningHint.isBlank() && !mergedPlanningHints.contains(planningHint)) {
                mergedPlanningHints.add(planningHint);
            }
        }

        return IntentDecision.builder()
                .intentId(mergedIntentId)
                .confidence(mergedConfidence)
                .routeMode(mergedRouteMode)
                .preferredAgentId(mergedPreferredAgentId)
                .attributes(mergedAttributes)
                .planningHints(mergedPlanningHints)
                .build();
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }
}
