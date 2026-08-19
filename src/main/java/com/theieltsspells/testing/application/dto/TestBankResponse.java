package com.theieltsspells.testing.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TestBankResponse(
        UUID id, String code, String title, String description, String purpose,
        SkillType skill, String testType, String difficulty, int sectionsCount,
        int totalQuestions, int durationMinutes, String version, String status,
        List<String> tags, int referencedCoursesCount, String createdBy,
        OffsetDateTime createdAt, OffsetDateTime updatedAt, Map<String, Object> builderContent
) {}
