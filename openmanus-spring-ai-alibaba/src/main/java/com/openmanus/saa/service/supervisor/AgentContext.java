package com.openmanus.saa.service.supervisor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Independent context for each AgentPeer.
 * Manages conversation history, working memory, and context resolution.
 */
public class AgentContext {

    private final String contextId;
    private final String peerId;
    private final List<ConversationMessage> conversationHistory;
    private final Map<String, Object> workingMemory;
    private final int maxHistoryTurns;
    private final int maxContextChars;

    public AgentContext(String peerId, int maxHistoryTurns, int maxContextChars) {
        this.contextId = UUID.randomUUID().toString();
        this.peerId = peerId;
        this.conversationHistory = new ArrayList<>();
        this.workingMemory = new LinkedHashMap<>();
        this.maxHistoryTurns = maxHistoryTurns;
        this.maxContextChars = maxContextChars;
    }

    public AgentContext(String peerId) {
        this(peerId, 10, 8000);
    }

    /**
     * Get the context ID.
     *
     * @return the context ID
     */
    public String getContextId() {
        return contextId;
    }

    /**
     * Get the peer ID this context belongs to.
     *
     * @return the peer ID
     */
    public String getPeerId() {
        return peerId;
    }

    /**
     * Add a message to the conversation history.
     *
     * @param role the role (user, assistant, system)
     * @param content the message content
     */
    public void addMessage(String role, String content) {
        conversationHistory.add(new ConversationMessage(role, content));
        trimHistoryIfNeeded();
    }

    /**
     * Add a user message to the history.
     *
     * @param content the message content
     */
    public void addUserMessage(String content) {
        addMessage("user", content);
    }

    /**
     * Add an assistant message to the history.
     *
     * @param content the message content
     */
    public void addAssistantMessage(String content) {
        addMessage("assistant", content);
    }

    /**
     * Add a system message to the history.
     *
     * @param content the message content
     */
    public void addSystemMessage(String content) {
        addMessage("system", content);
    }

    /**
     * Get the conversation history.
     *
     * @return unmodifiable list of messages
     */
    public List<ConversationMessage> getConversationHistory() {
        return Collections.unmodifiableList(conversationHistory);
    }

    /**
     * Store a value in working memory.
     *
     * @param key the key
     * @param value the value
     */
    public void putMemory(String key, Object value) {
        workingMemory.put(key, value);
    }

    /**
     * Get a value from working memory.
     *
     * @param key the key
     * @return the value or empty if not found
     */
    public Optional<Object> getMemory(String key) {
        return Optional.ofNullable(workingMemory.get(key));
    }

    /**
     * Get a typed value from working memory.
     *
     * @param key the key
     * @param type the expected type
     * @return the value or empty if not found or wrong type
     */
    public <T> Optional<T> getMemory(String key, Class<T> type) {
        Object value = workingMemory.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    /**
     * Remove a value from working memory.
     *
     * @param key the key
     */
    public void removeMemory(String key) {
        workingMemory.remove(key);
    }

    /**
     * Clear all working memory.
     */
    public void clearMemory() {
        workingMemory.clear();
    }

    /**
     * Get all working memory entries.
     *
     * @return unmodifiable map of memory
     */
    public Map<String, Object> getAllMemory() {
        return Collections.unmodifiableMap(workingMemory);
    }

    /**
     * Build a context prompt from the conversation history.
     * Truncates to fit within maxContextChars.
     *
     * @return the context prompt string
     */
    public String buildContextPrompt() {
        StringBuilder sb = new StringBuilder();
        List<ConversationMessage> history = getConversationHistory();

        // Build from most recent backwards
        for (int i = history.size() - 1; i >= 0; i--) {
            ConversationMessage msg = history.get(i);
            String entry = "[" + msg.role() + "]: " + msg.content() + "\n";
            if (sb.length() + entry.length() > maxContextChars) {
                break;
            }
            sb.insert(0, entry);
        }

        return sb.toString();
    }

    /**
     * Resolve a context reference from the shared context store.
     *
     * @param refId the reference ID
     * @param store the shared context store
     * @return the resolved content or empty if not found
     */
    public Optional<String> resolveContextRef(String refId, SharedContextStore store) {
        return store.getContext(refId);
    }

    /**
     * Clear the conversation history.
     */
    public void clearHistory() {
        conversationHistory.clear();
    }

    /**
     * Get the number of messages in history.
     *
     * @return the count
     */
    public int getHistorySize() {
        return conversationHistory.size();
    }

    /**
     * Check if the context is empty.
     *
     * @return true if no history or memory
     */
    public boolean isEmpty() {
        return conversationHistory.isEmpty() && workingMemory.isEmpty();
    }

    private void trimHistoryIfNeeded() {
        while (conversationHistory.size() > maxHistoryTurns) {
            conversationHistory.remove(0);
        }
    }

    /**
     * Record representing a conversation message.
     */
    public record ConversationMessage(String role, String content) {
    }
}
