package com.openmanus.saa.rag.api;

import com.openmanus.saa.rag.model.KnowledgeChunkRecord;
import com.openmanus.saa.rag.model.RetrievalHit;
import com.openmanus.saa.rag.model.RetrievalRequest;
import java.util.List;

public interface RagDocumentStore {

    void upsertChunks(List<KnowledgeChunkRecord> chunks);

    List<RetrievalHit> search(RetrievalRequest request, float[] queryVector);

    void deleteDocument(String knowledgeBaseId, String documentId);
}
