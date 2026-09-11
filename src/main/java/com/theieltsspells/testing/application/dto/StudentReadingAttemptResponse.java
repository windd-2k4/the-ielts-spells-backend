package com.theieltsspells.testing.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record StudentReadingAttemptResponse(
        UUID attemptId,
        UUID assignmentId,
        UUID testVersionId,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime expiresAt,
        long remainingSeconds,
        String title,
        String description,
        boolean allowResultAfterSubmit,
        List<Map<String, Object>> sections,
        List<SavedReadingResponse> responses,
        BigDecimal autoScore,
        BigDecimal finalScore
) {
    public record SavedReadingResponse(
            String questionKey,
            Map<String, Object> answer,
            int clientRevision,
            OffsetDateTime answeredAt
    ) {
    }
}
