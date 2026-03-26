package com.openmanus.saa.service.intent;

import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.ResponseMode;
import com.openmanus.saa.model.session.SessionState;
import com.openmanus.saa.service.RequestRoutingService;
import com.openmanus.saa.util.IntentResolutionHelper;
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

    public IntentResolution resolve(String prompt, SessionState session) {
        for (IntentRecognizer recognizer : recognizers) {
            Optional<IntentResolution> resolution = recognizer.recognize(prompt, session);
            if (resolution.isPresent()) {
                return enrichResponseMode(prompt, resolution.get());
            }
        }
        return requestRoutingService.resolveDefaultIntent(prompt);
    }

    private IntentResolution enrichResponseMode(String prompt, IntentResolution resolution) {
        if (IntentResolutionHelper.responseMode(resolution) != null) {
            return resolution;
        }
        ResponseMode responseMode = requestRoutingService.inferResponseMode(prompt);
        return IntentResolutionHelper.withResponseMode(resolution, responseMode);
    }
}
