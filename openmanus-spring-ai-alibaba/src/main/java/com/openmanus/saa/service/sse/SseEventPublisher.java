package com.openmanus.saa.service.sse;

import com.openmanus.saa.model.SseEvent;
import com.openmanus.saa.model.SseEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for publishing SSE events to subscribers.
 * Supports multiple subscribers per session.
 */
@Component
public class SseEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SseEventPublisher.class);

    // Session-based sinks for managing multiple concurrent streams
    private final Map<String, Sinks.Many<SseEvent>> sessionSinks = new ConcurrentHashMap<>();

    /**
     * Create a new Flux for a session that will receive SSE events.
     *
     * @param sessionId the session ID
     * @return a Flux of SSE events
     */
    public Flux<SseEvent> createFlux(String sessionId) {
        Sinks.Many<SseEvent> sink = Sinks.many().unicast().onBackpressureBuffer();
        sessionSinks.put(sessionId, sink);

        log.debug("Created SSE flux for session: {}", sessionId);

        return sink.asFlux()
                .doOnCancel(() -> {
                    log.debug("SSE stream cancelled for session: {}", sessionId);
                    cleanup(sessionId);
                })
                .doOnTerminate(() -> {
                    log.debug("SSE stream terminated for session: {}", sessionId);
                    cleanup(sessionId);
                });
    }

    /**
     * Create a Flux with a callback for when the subscriber is ready.
     *
     * @param sessionId the session ID
     * @param onSubscribe callback to execute when subscribed
     * @return a Flux of SSE events
     */
    public Flux<SseEvent> createFlux(String sessionId, Runnable onSubscribe) {
        return createFlux(sessionId)
                .doOnSubscribe(subscription -> {
                    log.debug("SSE stream subscribed for session: {}", sessionId);
                    if (onSubscribe != null) {
                        onSubscribe.run();
                    }
                });
    }

    /**
     * Publish an event to a specific session.
     *
     * @param sessionId the session ID
     * @param event the event to publish
     */
    public void publish(String sessionId, SseEvent event) {
        Sinks.Many<SseEvent> sink = sessionSinks.get(sessionId);
        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure()) {
                log.warn("Failed to emit SSE event for session {}: {}", sessionId, result);
            } else {
                log.debug("Published SSE event {} for session: {}", event.type(), sessionId);
            }
        } else {
            log.debug("No active SSE stream for session: {}", sessionId);
        }
    }

    /**
     * Publish an event using type and data.
     *
     * @param sessionId the session ID
     * @param type the event type
     * @param data the event data
     */
    public void publish(String sessionId, SseEventType type, Map<String, Object> data) {
        SseEvent event = new SseEvent(type, UUID.randomUUID().toString(), Instant.now(), data);
        publish(sessionId, event);
    }

    /**
     * Complete the stream for a session.
     *
     * @param sessionId the session ID
     */
    public void complete(String sessionId) {
        Sinks.Many<SseEvent> sink = sessionSinks.get(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
            log.debug("Completed SSE stream for session: {}", sessionId);
        }
    }

    /**
     * Emit an error and complete the stream for a session.
     *
     * @param sessionId the session ID
     * @param error the error
     */
    public void error(String sessionId, Throwable error) {
        Sinks.Many<SseEvent> sink = sessionSinks.get(sessionId);
        if (sink != null) {
            sink.tryEmitError(error);
            log.debug("Emitted error on SSE stream for session: {}", sessionId);
        }
    }

    /**
     * Clean up resources for a session.
     *
     * @param sessionId the session ID
     */
    public void cleanup(String sessionId) {
        Sinks.Many<SseEvent> sink = sessionSinks.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
        log.debug("Cleaned up SSE resources for session: {}", sessionId);
    }

    /**
     * Check if a session has an active stream.
     *
     * @param sessionId the session ID
     * @return true if there's an active stream
     */
    public boolean hasActiveStream(String sessionId) {
        return sessionSinks.containsKey(sessionId);
    }

    /**
     * Get the number of active streams.
     *
     * @return the count of active streams
     */
    public int getActiveStreamCount() {
        return sessionSinks.size();
    }
}
