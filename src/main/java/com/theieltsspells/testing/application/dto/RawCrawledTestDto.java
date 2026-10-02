package com.theieltsspells.testing.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillType;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record RawCrawledTestDto(
        UUID id,
        UUID sourceId,
        String sourceName,
        String sourceTestId,
        String title,
        SkillType skill,
        String sourceUrl,
        String status,
        UUID importedTestId,
        String errorMessage,
        OffsetDateTime crawledAt,
        OffsetDateTime importedAt,
        Map<String, Object> rawPayload,
        Map<String, Object> parsedStructure
) {}
