package com.openmanus.saa.rag.model;

import java.util.List;

public record RetrievalResult(
        String query,
        KnowledgeScope scope,
        List<RetrievalHit> hits
) {
}
