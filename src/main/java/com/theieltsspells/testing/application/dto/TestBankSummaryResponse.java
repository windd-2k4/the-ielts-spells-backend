package com.theieltsspells.testing.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TestBankSummaryResponse(
        UUID id, String code, String title, String description,
        SkillType skill, String testType, String format, int sectionsCount,
        int totalQuestions, int durationMinutes, String version, String status,
        List<String> tags, List<String> questionTypes,
        Map<String, Object> coverImage, Map<String, Object> writingTaskImage,
        int referencedCoursesCount, String createdBy,
        OffsetDateTime createdAt, OffsetDateTime updatedAt,
        int draftRevision, TestVersionResponse publishedVersion
) {}
