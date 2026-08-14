package com.theieltsspells.attendance.application.dto;

import java.util.List;

public record AttendanceSheetResponse(
        AttendanceSessionSummaryResponse session,
        List<AttendanceStudentRowResponse> students
) {}
