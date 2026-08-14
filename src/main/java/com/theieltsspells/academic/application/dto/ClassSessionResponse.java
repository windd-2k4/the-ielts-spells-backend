package com.theieltsspells.academic.application.dto;

import com.theieltsspells.shared.persistence.enums.SessionStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ClassSessionResponse(
        UUID id,
        UUID courseId,
        Short sessionNo,
        String title,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String zoomMeetingId,
        String zoomUrl,
        SessionStatus status,
        String notes,
        String phaseName,
        String content,
        UUID teacherId,
        String teacherName,
        List<CourseSessionItemResponse> items
) {}
