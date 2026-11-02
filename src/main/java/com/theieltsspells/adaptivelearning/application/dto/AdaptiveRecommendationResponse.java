package com.theieltsspells.adaptivelearning.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdaptiveRecommendationResponse(
        UUID id,
        String componentCode,
        String componentName,
        BigDecimal masteryProbability,
        BigDecimal confidence,
        int evidenceCount,
        String title,
        String reason,
        Integer durationMinutes,
        String difficulty,
        String href,
        OffsetDateTime createdAt
) {
}

