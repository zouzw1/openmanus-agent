package com.openmanus.saa.rag.api;

import com.openmanus.saa.rag.model.RetrievalRequest;
import com.openmanus.saa.rag.model.RetrievalResult;

public interface RagRetrievalService {

    RetrievalResult retrieve(RetrievalRequest request);
}
