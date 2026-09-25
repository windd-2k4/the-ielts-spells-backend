package com.theieltsspells.studentportal.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StudentCourseSessionResponse(
        UUID id,
        UUID courseId,
        Short sessionNo,
        String title,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String zoomUrl,
        String status,
        String phaseName,
        String teacherName
) {
}
