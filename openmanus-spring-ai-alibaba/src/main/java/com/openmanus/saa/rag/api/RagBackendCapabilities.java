package com.openmanus.saa.rag.api;

public interface RagBackendCapabilities {

    boolean supportsHybridSearch();

    boolean supportsMetadataFilter();

    boolean supportsDeletion();
}
