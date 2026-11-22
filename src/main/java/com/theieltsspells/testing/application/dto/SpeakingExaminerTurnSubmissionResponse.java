package com.theieltsspells.testing.application.dto;

import com.theieltsspells.testing.domain.SpeakingExamState;

import java.util.UUID;

public record SpeakingExaminerTurnSubmissionResponse(
        UUID sessionId,
        UUID studentTurnId,
        SpeakingExamState state,
        long stateVersion,
        int adaptiveFollowUpsUsed,
        SpeakingExaminerTurnResponse nextTurn,
        boolean examCompleted
) {}
