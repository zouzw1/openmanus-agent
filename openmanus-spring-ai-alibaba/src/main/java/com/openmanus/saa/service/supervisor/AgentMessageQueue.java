package com.openmanus.saa.service.supervisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Message queue for agent-to-agent communication.
 * Thread-safe queue with timeout support.
 */
public class AgentMessageQueue {

    private static final Logger log = LoggerFactory.getLogger(AgentMessageQueue.class);

    private final String peerId;
    private final BlockingQueue<AgentMessage> queue;
    private final int maxSize;

    public AgentMessageQueue(String peerId, int maxSize) {
        this.peerId = peerId;
        this.maxSize = maxSize;
        this.queue = new LinkedBlockingQueue<>(maxSize);
    }

    public AgentMessageQueue(String peerId) {
        this(peerId, 100);
    }

    /**
     * Get the peer ID this queue belongs to.
     *
     * @return the peer ID
     */
    public String getPeerId() {
        return peerId;
    }

    /**
     * Add a message to the queue.
     *
     * @param message the message to add
     * @return true if added successfully
     */
    public boolean offer(AgentMessage message) {
        boolean added = queue.offer(message);
        if (added) {
            log.debug("Message {} queued for peer {}", message.messageId(), peerId);
        } else {
            log.warn("Message queue full for peer {}, dropping message {}", peerId, message.messageId());
        }
        return added;
    }

    /**
     * Add a message to the queue, waiting if necessary.
     *
     * @param message the message to add
     * @throws InterruptedException if interrupted while waiting
     */
    public void put(AgentMessage message) throws InterruptedException {
        queue.put(message);
        log.debug("Message {} queued for peer {}", message.messageId(), peerId);
    }

    /**
     * Retrieve and remove the next message, waiting if necessary.
     *
     * @return the next message
     * @throws InterruptedException if interrupted while waiting
     */
    public AgentMessage take() throws InterruptedException {
        AgentMessage message = queue.take();
        log.debug("Message {} retrieved by peer {}", message.messageId(), peerId);
        return message;
    }

    /**
     * Retrieve and remove the next message with a timeout.
     *
     * @param timeout the timeout
     * @param unit the time unit
     * @return the next message or null if timeout
     * @throws InterruptedException if interrupted while waiting
     */
    public AgentMessage poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    /**
     * Retrieve and remove all pending messages.
     *
     * @return list of all pending messages
     */
    public List<AgentMessage> drainAll() {
        List<AgentMessage> messages = new ArrayList<>();
        queue.drainTo(messages);
        return messages;
    }

    /**
     * Get all pending messages without removing them.
     *
     * @return unmodifiable list of pending messages
     */
    public List<AgentMessage> peekAll() {
        return Collections.unmodifiableList(new ArrayList<>(queue));
    }

    /**
     * Get the number of pending messages.
     *
     * @return the queue size
     */
    public int size() {
        return queue.size();
    }

    /**
     * Check if the queue is empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Clear all messages from the queue.
     */
    public void clear() {
        queue.clear();
    }

    /**
     * Get the maximum queue size.
     *
     * @return the max size
     */
    public int getMaxSize() {
        return maxSize;
    }
}
