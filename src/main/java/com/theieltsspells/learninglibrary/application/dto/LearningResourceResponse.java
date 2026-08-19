package com.theieltsspells.learninglibrary.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LearningResourceResponse(
        UUID id, String code, String title, String description, SkillType skill,
        String category, String resourceType, String scope, UUID courseId,
        String externalUrl, boolean teacherOnly, String status, UUID createdBy,
        OffsetDateTime createdAt, OffsetDateTime updatedAt
) {}
