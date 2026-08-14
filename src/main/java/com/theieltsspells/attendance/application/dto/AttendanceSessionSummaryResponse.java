package com.theieltsspells.attendance.application.dto;

import com.theieltsspells.attendance.domain.AttendanceSheetStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AttendanceSessionSummaryResponse(
        UUID sessionId,
        short sessionNo,
        String title,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String teacherName,
        AttendanceSheetStatus sheetStatus,
        int totalStudents,
        int markedCount,
        int presentCount,
        int lateCount,
        int leftEarlyCount,
        int absentCount,
        int excusedCount,
        int pendingCount,
        BigDecimal attendanceRate,
        OffsetDateTime lockedAt
) {}
