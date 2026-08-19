package com.theieltsspells.learninglibrary.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LearningResourceRequest(
        @NotBlank String title,
        String description,
        @NotNull SkillType skill,
        @NotBlank String category,
        @NotBlank String resourceType,
        @NotBlank String scope,
        UUID courseId,
        String externalUrl,
        Boolean teacherOnly,
        @NotBlank String status
) {}
