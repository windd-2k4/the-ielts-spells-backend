package com.theieltsspells.testing.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TestAttemptQuestionReviewResponse(
        UUID attemptId,
        String questionKey,
        String reasonCode,
        String note,
        boolean needsRetry,
        String vocabularyTerm,
        String vocabularyMeaning,
        OffsetDateTime reviewedAt,
        OffsetDateTime updatedAt
) {
}
