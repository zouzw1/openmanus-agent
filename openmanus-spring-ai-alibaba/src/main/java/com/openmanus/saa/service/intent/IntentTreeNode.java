package com.openmanus.saa.service.intent;

import com.openmanus.saa.model.session.Session;
import java.util.List;

public interface IntentTreeNode {

    String id();

    boolean matches(String prompt, Session session);

    default IntentDecision decision(String prompt, Session session) {
        return IntentDecision.empty();
    }

    default List<IntentTreeNode> children() {
        return List.of();
    }
}
