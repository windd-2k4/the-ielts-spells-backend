package com.theieltsspells.testing.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TestAssignmentResponse(
        UUID id,
        UUID testId,
        UUID testVersionId,
        String testTitle,
        String versionLabel,
        String skill,
        UUID courseId,
        String courseName,
        String mode,
        OffsetDateTime opensAt,
        OffsetDateTime closesAt,
        short maxAttempts,
        Integer durationSeconds,
        boolean showResultAfterSubmit,
        OffsetDateTime archivedAt,
        OffsetDateTime createdAt
) {
}
