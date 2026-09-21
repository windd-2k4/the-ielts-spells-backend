package com.theieltsspells.testing.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record StudentPracticeCatalogItemResponse(
        UUID testId,
        UUID testVersionId,
        String code,
        String title,
        String description,
        SkillType skill,
        String testType,
        String format,
        int sectionsCount,
        int totalItems,
        int durationMinutes,
        List<String> tags,
        List<String> questionTypes,
        Map<String, Object> coverImage,
        OffsetDateTime publishedAt,
        int attemptsCount,
        UUID activeAttemptId,
        OffsetDateTime activeAttemptExpiresAt,
        BigDecimal lastScore,
        boolean deliveryReady
) {
}
