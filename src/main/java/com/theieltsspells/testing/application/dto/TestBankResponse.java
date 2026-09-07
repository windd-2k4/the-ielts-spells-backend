package com.theieltsspells.testing.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TestBankResponse(
        UUID id, String code, String title, String description,
        SkillType skill, String testType, int sectionsCount,
        int totalQuestions, int durationMinutes, String version, String status,
        List<String> tags, int referencedCoursesCount, String createdBy,
        OffsetDateTime createdAt, OffsetDateTime updatedAt, Map<String, Object> builderContent,
        int draftRevision, TestVersionResponse publishedVersion
) {}
