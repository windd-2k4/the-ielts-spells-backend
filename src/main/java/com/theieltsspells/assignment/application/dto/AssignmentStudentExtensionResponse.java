package com.theieltsspells.assignment.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssignmentStudentExtensionResponse(
        UUID studentId,
        OffsetDateTime extensionDueAt,
        String reason
) {}
