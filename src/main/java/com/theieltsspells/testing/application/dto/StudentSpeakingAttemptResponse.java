package com.theieltsspells.testing.application.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StudentSpeakingAttemptResponse(
        UUID attemptId,
        UUID testVersionId,
        String title,
        String description,
        int durationMinutes,
        long remainingSeconds,
        Long pausedRemainingSeconds,
        boolean isPaused,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime expiresAt,
        boolean allowResultAfterSubmit,
        List<SpeakingPartResponse> parts,
        List<SpeakingSavedResponse> responses
) {}
