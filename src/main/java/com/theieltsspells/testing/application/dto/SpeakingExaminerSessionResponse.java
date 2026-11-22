package com.theieltsspells.testing.application.dto;

import com.theieltsspells.testing.domain.SpeakingExamState;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SpeakingExaminerSessionResponse(
        UUID sessionId,
        UUID attemptId,
        SpeakingExamMode mode,
        SpeakingExamState state,
        long stateVersion,
        int adaptiveFollowUpsUsed,
        Policy policy,
        Versions versions,
        SpeakingExaminerTurnResponse currentTurn,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
    public record Policy(
            int maxAdaptiveFollowUps,
            boolean feedbackDuringExam,
            boolean part2AndPart3ShareTopic,
            ResultType resultType
    ) {}

    public enum ResultType {
        ESTIMATED_BAND
    }

    public record Versions(
            String promptVersion,
            String rubricVersion,
            String speechModelVersion,
            String featureExtractorVersion
    ) {}
}
