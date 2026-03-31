package com.openmanus.saa.service.supervisor;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store for shared context between agents.
 * Allows agents to share data through context references.
 */
public class SharedContextStore {

    private final Map<String, String> contextData;
    private final Map<String, ContextMetadata> contextMetadata;

    public SharedContextStore() {
        this.contextData = new ConcurrentHashMap<>();
        this.contextMetadata = new ConcurrentHashMap<>();
    }

    /**
     * Store context data with a reference ID.
     *
     * @param refId the reference ID
     * @param content the content to store
     * @param sourcePeerId the peer ID that created this context
     */
    public void putContext(String refId, String content, String sourcePeerId) {
        contextData.put(refId, content);
        contextMetadata.put(refId, new ContextMetadata(sourcePeerId, System.currentTimeMillis()));
    }

    /**
     * Store context data with a reference ID (no source tracking).
     *
     * @param refId the reference ID
     * @param content the content to store
     */
    public void putContext(String refId, String content) {
        contextData.put(refId, content);
    }

    /**
     * Get context data by reference ID.
     *
     * @param refId the reference ID
     * @return the content or empty if not found
     */
    public Optional<String> getContext(String refId) {
        return Optional.ofNullable(contextData.get(refId));
    }

    /**
     * Get context metadata by reference ID.
     *
     * @param refId the reference ID
     * @return the metadata or empty if not found
     */
    public Optional<ContextMetadata> getMetadata(String refId) {
        return Optional.ofNullable(contextMetadata.get(refId));
    }

    /**
     * Check if context exists.
     *
     * @param refId the reference ID
     * @return true if exists
     */
    public boolean hasContext(String refId) {
        return contextData.containsKey(refId);
    }

    /**
     * Remove context by reference ID.
     *
     * @param refId the reference ID
     * @return the removed content or empty if not found
     */
    public Optional<String> removeContext(String refId) {
        contextMetadata.remove(refId);
        return Optional.ofNullable(contextData.remove(refId));
    }

    /**
     * Clear all stored context.
     */
    public void clear() {
        contextData.clear();
        contextMetadata.clear();
    }

    /**
     * Get the number of stored contexts.
     *
     * @return the count
     */
    public int size() {
        return contextData.size();
    }

    /**
     * Metadata for stored context.
     */
    public record ContextMetadata(String sourcePeerId, long createdAt) {
    }
}
