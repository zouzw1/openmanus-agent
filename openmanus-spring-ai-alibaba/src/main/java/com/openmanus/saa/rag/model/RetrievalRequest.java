package com.openmanus.saa.rag.model;

import java.util.Map;

public record RetrievalRequest(
        String query,
        KnowledgeScope scope,
        int topK,
        boolean hybrid,
        Map<String, Object> filters
) {
}
