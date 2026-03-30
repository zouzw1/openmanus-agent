package com.openmanus.saa.rag.api;

import com.openmanus.saa.rag.model.IngestRequest;
import com.openmanus.saa.rag.model.IngestResult;

public interface RagIngestionService {

    IngestResult ingest(IngestRequest request);
}
