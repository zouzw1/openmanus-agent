package com.openmanus.saa.rag.model;

import java.util.List;
import java.util.Map;

public record IngestRequest(
        String knowledgeBaseId,
        List<KnowledgeDocument> documents,
        Map<String, Object> options
) {
}
