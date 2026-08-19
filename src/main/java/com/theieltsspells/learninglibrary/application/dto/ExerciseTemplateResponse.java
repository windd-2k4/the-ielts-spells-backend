package com.theieltsspells.learninglibrary.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ExerciseTemplateResponse(
        UUID id, String code, String title, String instructions, SkillType skill,
        String category, String exerciseType, String completionMode, String scope,
        UUID courseId, String sourceUrl, Short durationMinutes, BigDecimal maxScore,
        Short attemptLimit, boolean requiresTeacherReview, Map<String, Object> content,
        Map<String, Object> answerKey, String status, UUID createdBy,
        OffsetDateTime createdAt, OffsetDateTime updatedAt
) {}
