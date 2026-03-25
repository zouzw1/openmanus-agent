package com.openmanus.saa.service.intent;

import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.session.SessionState;
import java.util.Optional;

public interface IntentRecognizer {

    Optional<IntentResolution> recognize(String prompt, SessionState session);
}
