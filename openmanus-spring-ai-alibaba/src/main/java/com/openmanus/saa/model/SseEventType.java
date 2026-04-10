package com.openmanus.saa.model;

/**
 * Enumeration of SSE event types for streaming responses.
 */
public enum SseEventType {
    // Session level events
    SESSION_STARTED,      // Session started
    INTENT_RESOLVED,      // Intent resolution completed (includes execution mode)

    // Step level events
    STEP_STARTED,         // Step started
    STEP_COMPLETED,       // Step completed

    // Agent level events (multi-agent specific)
    AGENT_STARTED,        // Agent started
    TASK_COMPLETED,       // Task completed

    // Result level events
    ARTIFACT_CREATED,     // Artifact generated
    EXECUTION_COMPLETED,  // Execution completed (includes final result)
    ERROR                 // Error occurred
}
