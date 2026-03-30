package com.openmanus.saa.rag.model;

import java.util.List;

public record IngestResult(
        String knowledgeBaseId,
        int documentCount,
        int chunkCount,
        List<String> documentIds
) {
}
