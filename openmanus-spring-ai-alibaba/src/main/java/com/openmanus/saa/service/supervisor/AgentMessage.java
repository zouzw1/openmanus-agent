package com.openmanus.saa.service.supervisor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Message for agent-to-agent communication.
 */
public record AgentMessage(
        String messageId,
        String fromPeerId,
        String toPeerId,
        MessageType type,
        String content,
        Map<String, Object> metadata,
        Instant timestamp
) {
    public AgentMessage {
        messageId = messageId == null ? UUID.randomUUID().toString() : messageId;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    /**
     * Message types for agent communication.
     */
    public enum MessageType {
        /**
         * Request to execute a task
         */
        TASK_REQUEST,
        /**
         * Notification of task result
         */
        TASK_RESULT,
        /**
         * Sharing context with another agent
         */
        CONTEXT_SHARE,
        /**
         * Request for clarification
         */
        CLARIFICATION,
        /**
         * Broadcast message to all agents
         */
        BROADCAST,
        /**
         * Error notification
         */
        ERROR
    }

    /**
     * Create a direct message from one agent to another.
     *
     * @param from the sender peer ID
     * @param to the receiver peer ID
     * @param content the message content
     * @return the message
     */
    public static AgentMessage direct(String from, String to, String content) {
        return new AgentMessage(
                null, from, to, MessageType.TASK_REQUEST, content, Map.of(), null
        );
    }

    /**
     * Create a direct message with a specific type.
     *
     * @param from the sender peer ID
     * @param to the receiver peer ID
     * @param type the message type
     * @param content the message content
     * @return the message
     */
    public static AgentMessage direct(String from, String to, MessageType type, String content) {
        return new AgentMessage(
                null, from, to, type, content, Map.of(), null
        );
    }

    /**
     * Create a broadcast message from an agent to all agents.
     *
     * @param from the sender peer ID
     * @param content the message content
     * @return the message
     */
    public static AgentMessage broadcast(String from, String content) {
        return new AgentMessage(
                null, from, null, MessageType.BROADCAST, content, Map.of(), null
        );
    }

    /**
     * Create a broadcast message with metadata.
     *
     * @param from the sender peer ID
     * @param content the message content
     * @param metadata additional metadata
     * @return the message
     */
    public static AgentMessage broadcast(String from, String content, Map<String, Object> metadata) {
        return new AgentMessage(
                null, from, null, MessageType.BROADCAST, content, metadata, null
        );
    }

    /**
     * Create a context share message.
     *
     * @param from the sender peer ID
     * @param to the receiver peer ID
     * @param content the context content
     * @param contextRef the context reference ID
     * @return the message
     */
    public static AgentMessage contextShare(String from, String to, String content, String contextRef) {
        return new AgentMessage(
                null, from, to, MessageType.CONTEXT_SHARE, content,
                Map.of("contextRef", contextRef), null
        );
    }

    /**
     * Create an error message.
     *
     * @param from the sender peer ID
     * @param to the receiver peer ID (or null for broadcast)
     * @param errorMessage the error message
     * @return the message
     */
    public static AgentMessage error(String from, String to, String errorMessage) {
        return new AgentMessage(
                null, from, to, MessageType.ERROR, errorMessage, Map.of(), null
        );
    }

    /**
     * Check if this message is a broadcast.
     *
     * @return true if broadcast
     */
    public boolean isBroadcast() {
        return toPeerId == null || type == MessageType.BROADCAST;
    }
}
