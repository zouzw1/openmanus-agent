package com.openmanus.saa.service.intent;

import com.openmanus.saa.model.AgentRequest;
import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.IntentRouteMode;
import com.openmanus.saa.model.ResponseMode;
import com.openmanus.saa.model.session.Session;
import com.openmanus.saa.service.RequestRoutingService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;

@Service
public class IntentResolutionService {

    private final List<IntentRecognizer> recognizers;
    private final RequestRoutingService requestRoutingService;

    public IntentResolutionService(List<IntentRecognizer> recognizers, RequestRoutingService requestRoutingService) {
        List<IntentRecognizer> orderedRecognizers = recognizers == null ? new ArrayList<>() : new ArrayList<>(recognizers);
        AnnotationAwareOrderComparator.sort(orderedRecognizers);
        this.recognizers = List.copyOf(orderedRecognizers);
        this.requestRoutingService = requestRoutingService;
    }

    public IntentResolution resolve(String prompt, Session session) {
        for (IntentRecognizer recognizer : recognizers) {
            Optional<IntentResolution> resolution = recognizer.recognize(prompt, session);
            if (resolution.isPresent()) {
                return enrichResponseMode(prompt, resolution.get());
            }
        }
        return requestRoutingService.resolveDefaultIntent(prompt);
    }

    /**
     * Resolve intent with mode override from request.
     * If the request forces a specific execution mode, override the detected mode.
     *
     * @param prompt the user prompt
     * @param session the session state
     * @param request the agent request containing mode override
     * @return the resolved intent
     */
    public IntentResolution resolve(String prompt, Session session, AgentRequest request) {
        IntentResolution resolution = resolve(prompt, session);

        if (request != null) {
            // Handle forced execution mode
            if (request.isForceMultiAgent()) {
                return overrideRouteMode(resolution, IntentRouteMode.MULTI_AGENT);
            } else if (request.isForceSingleAgent()) {
                // Force single agent: use PLAN_EXECUTE or DIRECT_CHAT based on complexity
                if (resolution.routeMode() == IntentRouteMode.MULTI_AGENT) {
                    return overrideRouteMode(resolution, IntentRouteMode.PLAN_EXECUTE);
                }
            }
        }

        return resolution;
    }

    private IntentResolution overrideRouteMode(IntentResolution resolution, IntentRouteMode newMode) {
        return new IntentResolution(
                resolution.intentId(),
                resolution.confidence(),
                newMode,
                resolution.preferredAgentId(),
                resolution.attributes(),
                resolution.planningHints()
        );
    }

    private static final String RESPONSE_MODE_ATTRIBUTE = "responseMode";

    private IntentResolution enrichResponseMode(String prompt, IntentResolution resolution) {
        // 检查是否已有 responseMode
        ResponseMode existing = ResponseMode.from(resolution.attributes().get(RESPONSE_MODE_ATTRIBUTE));
        if (existing != null) {
            return resolution;
        }
        ResponseMode responseMode = requestRoutingService.inferResponseMode(prompt);
        // 添加 responseMode 到 attributes
        java.util.Map<String, Object> attributes = new java.util.LinkedHashMap<>(resolution.attributes());
        attributes.put(RESPONSE_MODE_ATTRIBUTE, responseMode.name());
        return new IntentResolution(
                resolution.intentId(),
                resolution.confidence(),
                resolution.routeMode(),
                resolution.preferredAgentId(),
                attributes,
                resolution.planningHints()
        );
    }
}
