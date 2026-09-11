package com.theieltsspells.academic.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CourseStudentSupportAssignmentResponse(
        UUID studentSupportId,
        UUID assignedBy,
        OffsetDateTime assignedAt
) {
}
