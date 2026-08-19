package com.theieltsspells.learninglibrary.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record ExerciseTemplateRequest(
        @NotBlank String title,
        String instructions,
        @NotNull SkillType skill,
        @NotBlank String category,
        @NotBlank String exerciseType,
        @NotBlank String completionMode,
        @NotBlank String scope,
        UUID courseId,
        String sourceUrl,
        @Positive Short durationMinutes,
        @DecimalMin(value = "0.01") BigDecimal maxScore,
        @Positive Short attemptLimit,
        Boolean requiresTeacherReview,
        Map<String, Object> content,
        Map<String, Object> answerKey,
        @NotBlank String status
) {}
