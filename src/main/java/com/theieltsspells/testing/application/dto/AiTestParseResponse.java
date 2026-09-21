package com.theieltsspells.testing.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillType;

import java.util.List;
import java.util.Map;

public record AiTestParseResponse(
        String title,
        String description,
        SkillType skill,
        String testType,
        Integer durationMinutes,
        List<String> suggestedTags,
        Map<String, Object> builderContent,
        Integer questionCount,
        List<String> warnings
) {}
