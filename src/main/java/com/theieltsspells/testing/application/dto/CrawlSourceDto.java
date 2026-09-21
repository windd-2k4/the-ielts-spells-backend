package com.theieltsspells.testing.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillType;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record CrawlSourceDto(
        UUID id,
        String name,
        String sourceUrl,
        String crawlerType,
        SkillType targetSkill,
        Map<String, Object> config,
        String scheduleCron,
        boolean isActive,
        OffsetDateTime lastCrawledAt,
        String lastStatus,
        int totalCrawled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
