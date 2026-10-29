package com.theieltsspells.assignment.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StudentSubmissionRevisionResponse(
        UUID submissionId,
        UUID attemptId,
        short attemptNo,
        String status,
        String textContent,
        OffsetDateTime submittedAt,
        BigDecimal score,
        String reviewStatus,
        String reflection,
        String feedback,
        OffsetDateTime feedbackAt,
        boolean audioSubmitted,
        OffsetDateTime extensionDueAt,
        OffsetDateTime reflectionDueAt
) {}
