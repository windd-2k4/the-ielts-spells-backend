package com.theieltsspells.academic.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CourseTeacherAssignmentResponse(
        UUID teacherId,
        String fullName,
        String email,
        boolean primary,
        OffsetDateTime assignedAt
) {
}

