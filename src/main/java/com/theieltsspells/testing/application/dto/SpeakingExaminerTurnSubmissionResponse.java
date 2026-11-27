package com.theieltsspells.testing.application.dto;

import com.theieltsspells.testing.domain.SpeakingExamState;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SpeakingExaminerTurnSubmissionResponse(
        UUID sessionId,
        UUID studentTurnId,
        SpeakingExamState state,
        long stateVersion,
        int adaptiveFollowUpsUsed,
        SpeakingExaminerTurnResponse nextTurn,
        boolean examCompleted,
        LatencyMetrics latency,
        OffsetDateTime part2PrepDeadlineAt,
        Integer part2PrepRemainingSeconds
) {
    public SpeakingExaminerTurnSubmissionResponse(
            UUID sessionId,
            UUID studentTurnId,
            SpeakingExamState state,
            long stateVersion,
            int adaptiveFollowUpsUsed,
            SpeakingExaminerTurnResponse nextTurn,
            boolean examCompleted
    ) {
        this(sessionId, studentTurnId, state, stateVersion, adaptiveFollowUpsUsed, nextTurn, examCompleted,
                null, null, null);
    }

    public SpeakingExaminerTurnSubmissionResponse(
            UUID sessionId,
            UUID studentTurnId,
            SpeakingExamState state,
            long stateVersion,
            int adaptiveFollowUpsUsed,
            SpeakingExaminerTurnResponse nextTurn,
            boolean examCompleted,
            LatencyMetrics latency
    ) {
        this(sessionId, studentTurnId, state, stateVersion, adaptiveFollowUpsUsed, nextTurn, examCompleted,
                latency, null, null);
    }

    public record LatencyMetrics(
            long storageReadMs,
            long sttProcessingMs,
            long followUpGenerationMs,
            long ttsProcessingMs,
            long serverTurnMs
    ) {}
}
