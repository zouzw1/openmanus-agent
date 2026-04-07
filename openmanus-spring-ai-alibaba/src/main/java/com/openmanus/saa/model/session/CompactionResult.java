package com.openmanus.saa.model.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CompactionResult(
    String summary,
    String formattedSummary,
    Session compactedSession,
    int removedMessageCount
) {
    @JsonCreator
    public CompactionResult(
        @JsonProperty("summary") String summary,
        @JsonProperty("formattedSummary") String formattedSummary,
        @JsonProperty("compactedSession") Session compactedSession,
        @JsonProperty("removedMessageCount") int removedMessageCount
    ) {
        this.summary = summary;
        this.formattedSummary = formattedSummary;
        this.compactedSession = compactedSession;
        this.removedMessageCount = removedMessageCount;
    }

    public static CompactionResult unchanged(Session session) {
        return new CompactionResult("", "", session, 0);
    }

    public boolean wasCompacted() {
        return removedMessageCount > 0;
    }
}