package com.openmanus.saa.rag.api;

import com.openmanus.saa.rag.model.KnowledgeChunk;
import com.openmanus.saa.rag.model.KnowledgeDocument;
import java.util.List;

public interface ChunkingStrategy {

    List<KnowledgeChunk> chunk(KnowledgeDocument document);
}
