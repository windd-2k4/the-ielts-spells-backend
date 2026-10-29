package com.theieltsspells.assignment.application.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateAssignmentRequest(
        @Size(max = 180) String title,
        @Size(max = 4000) String description,
        @Size(max = 30) String assignmentType,
        OffsetDateTime opensAt,
        OffsetDateTime dueAt,
        BigDecimal maxScore,
        Boolean allowResubmission,
        String latePolicy,
        Integer lateGraceMinutes,
        OffsetDateTime reflectionDueAt
) {}
