package com.theieltsspells.testing.application.dto;

import com.theieltsspells.testing.domain.SpeakingExamState;
import com.theieltsspells.testing.domain.SpeakingSessionVariant;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SpeakingExaminerSessionResponse(
        UUID sessionId,
        UUID attemptId,
        SpeakingExamMode mode,
        SpeakingSessionVariant variant,
        List<Integer> partsIncluded,
        boolean overallBandAvailable,
        SpeakingExamState state,
        long stateVersion,
        int adaptiveFollowUpsUsed,
        Policy policy,
        Versions versions,
        SpeakingExaminerTurnResponse currentTurn,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime part2PrepDeadlineAt,
        Integer part2PrepRemainingSeconds
) {
    public SpeakingExaminerSessionResponse(
            UUID sessionId,
            UUID attemptId,
            SpeakingExamMode mode,
            SpeakingSessionVariant variant,
            List<Integer> partsIncluded,
            boolean overallBandAvailable,
            SpeakingExamState state,
            long stateVersion,
            int adaptiveFollowUpsUsed,
            Policy policy,
            Versions versions,
            SpeakingExaminerTurnResponse currentTurn,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt
    ) {
        this(sessionId, attemptId, mode, variant, partsIncluded, overallBandAvailable, state, stateVersion,
                adaptiveFollowUpsUsed, policy, versions, currentTurn, startedAt, completedAt, null, null);
    }

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
