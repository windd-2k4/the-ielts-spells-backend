package com.theieltsspells.testing.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record SaveCrawlSourceRequest(
        @NotBlank String name,
        @NotBlank String sourceUrl,
        String crawlerType,
        @NotNull SkillType targetSkill,
        Map<String, Object> config,
        String scheduleCron,
        Boolean isActive
) {}
