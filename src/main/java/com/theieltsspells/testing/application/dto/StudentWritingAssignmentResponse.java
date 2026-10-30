package com.theieltsspells.testing.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StudentWritingAssignmentResponse(
        UUID assignmentId,
        UUID testVersionId,
        String title,
        String versionLabel,
        UUID courseId,
        String courseName,
        UUID classActivityId,
        UUID sessionId,
        Short sessionNo,
        String sessionTitle,
        String mode,
        OffsetDateTime opensAt,
        OffsetDateTime closesAt,
        short maxAttempts,
        int attemptsUsed,
        Integer durationSeconds,
        OffsetDateTime activeAttemptExpiresAt
) {
}
