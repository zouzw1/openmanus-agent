package com.openmanus.saa.service.intent;

import com.openmanus.saa.model.session.SessionState;
import java.util.List;

public interface IntentTreeNode {

    String id();

    boolean matches(String prompt, SessionState session);

    default IntentDecision decision(String prompt, SessionState session) {
        return IntentDecision.empty();
    }

    default List<IntentTreeNode> children() {
        return List.of();
    }
}
