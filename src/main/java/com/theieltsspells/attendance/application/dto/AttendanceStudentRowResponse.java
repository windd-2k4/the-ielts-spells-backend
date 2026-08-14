package com.theieltsspells.attendance.application.dto;

import com.theieltsspells.shared.persistence.enums.AttendanceStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AttendanceStudentRowResponse(
        UUID recordId,
        UUID studentId,
        String studentCode,
        String fullName,
        String email,
        String avatarPath,
        AttendanceStatus status,
        OffsetDateTime joinedAt,
        OffsetDateTime leftAt,
        Integer durationSeconds,
        String source,
        String note
) {}
