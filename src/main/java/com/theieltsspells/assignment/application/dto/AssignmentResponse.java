package com.theieltsspells.assignment.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AssignmentResponse(
        UUID id,
        UUID courseId,
        UUID sessionId,
        UUID classActivityId,
        UUID activityId,
        String title,
        String description,
        String assignmentType,
        OffsetDateTime opensAt,
        OffsetDateTime dueAt,
        BigDecimal maxScore,
        boolean allowResubmission,
        String status,
        String latePolicy,
        int lateGraceMinutes,
        OffsetDateTime reflectionDueAt
) {}
