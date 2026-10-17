package com.theieltsspells.attendance.application.dto;

import com.theieltsspells.shared.persistence.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AttendanceEntryRequest(
        @NotNull UUID studentId,
        @NotNull AttendanceStatus status,
        OffsetDateTime joinedAt,
        OffsetDateTime leftAt,
        Integer durationSeconds,
        @PositiveOrZero Integer interactionCount,
        String note
) {}
