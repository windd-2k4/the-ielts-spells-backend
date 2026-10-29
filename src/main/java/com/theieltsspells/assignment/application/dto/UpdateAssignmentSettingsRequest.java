package com.theieltsspells.assignment.application.dto;

import java.time.OffsetDateTime;

public record UpdateAssignmentSettingsRequest(
        OffsetDateTime opensAt,
        OffsetDateTime dueAt,
        Boolean allowResubmission,
        String latePolicy,
        Integer lateGraceMinutes,
        OffsetDateTime reflectionDueAt
) {}
