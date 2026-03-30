package com.openmanus.saa.rag.elasticsearch;

import com.openmanus.saa.rag.api.RagBackendCapabilities;

public class ElasticsearchRagBackendCapabilities implements RagBackendCapabilities {

    @Override
    public boolean supportsHybridSearch() {
        return true;
    }

    @Override
    public boolean supportsMetadataFilter() {
        return true;
    }

    @Override
    public boolean supportsDeletion() {
        return true;
    }
}
