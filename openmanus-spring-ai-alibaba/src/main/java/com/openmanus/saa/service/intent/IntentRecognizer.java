package com.openmanus.saa.service.intent;

import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.session.Session;
import java.util.Optional;

public interface IntentRecognizer {

    Optional<IntentResolution> recognize(String prompt, Session session);
}
