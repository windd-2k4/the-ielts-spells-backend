package com.theieltsspells.testing.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StudentReadingCatalogItemResponse(
        UUID testId,
        UUID testVersionId,
        String code,
        String title,
        String description,
        String testType,
        int sectionsCount,
        int totalQuestions,
        int durationMinutes,
        List<String> tags,
        OffsetDateTime publishedAt,
        int attemptsCount,
        UUID activeAttemptId,
        OffsetDateTime activeAttemptExpiresAt,
        BigDecimal lastScore
) {
}
