package com.theieltsspells.attendance.application.dto;

import com.theieltsspells.shared.persistence.enums.AttendanceStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AttendanceResponse(UUID id, UUID sessionId, UUID studentId, AttendanceStatus status,
        OffsetDateTime joinedAt, OffsetDateTime leftAt, Integer durationSeconds,
        boolean confirmed, String adjustmentReason) {}
