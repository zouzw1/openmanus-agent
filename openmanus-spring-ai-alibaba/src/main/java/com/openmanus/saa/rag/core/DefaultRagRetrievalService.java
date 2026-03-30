package com.openmanus.saa.rag.core;

import com.openmanus.saa.rag.api.EmbeddingProvider;
import com.openmanus.saa.rag.api.RagDocumentStore;
import com.openmanus.saa.rag.api.RagRetrievalService;
import com.openmanus.saa.rag.model.RetrievalHit;
import com.openmanus.saa.rag.model.RetrievalRequest;
import com.openmanus.saa.rag.model.RetrievalResult;
import java.util.List;

public class DefaultRagRetrievalService implements RagRetrievalService {

    private final RagDocumentStore documentStore;
    private final EmbeddingProvider embeddingProvider;

    public DefaultRagRetrievalService(RagDocumentStore documentStore, EmbeddingProvider embeddingProvider) {
        this.documentStore = documentStore;
        this.embeddingProvider = embeddingProvider;
    }

    @Override
    public RetrievalResult retrieve(RetrievalRequest request) {
        float[] queryVector = embeddingProvider.embed(request.query());
        List<RetrievalHit> hits = documentStore.search(request, queryVector);
        return new RetrievalResult(request.query(), request.scope(), hits);
    }
}
