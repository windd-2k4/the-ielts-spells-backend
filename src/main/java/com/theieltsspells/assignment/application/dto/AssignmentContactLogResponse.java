package com.theieltsspells.assignment.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssignmentContactLogResponse(
        UUID id,
        UUID studentId,
        String studentName,
        UUID actorId,
        String actorName,
        String channel,
        String action,
        String message,
        OffsetDateTime createdAt
) {}
