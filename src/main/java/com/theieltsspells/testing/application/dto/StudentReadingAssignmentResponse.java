package com.theieltsspells.testing.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StudentReadingAssignmentResponse(
        UUID assignmentId,
        UUID testVersionId,
        String title,
        String versionLabel,
        UUID courseId,
        String courseName,
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
