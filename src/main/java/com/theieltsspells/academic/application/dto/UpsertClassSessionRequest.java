package com.theieltsspells.academic.application.dto;

import com.theieltsspells.shared.persistence.enums.SessionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UpsertClassSessionRequest(
        @NotNull @Positive Short sessionNo,
        String title,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        String zoomMeetingId,
        String zoomUrl,
        @NotNull SessionStatus status,
        String notes,
        String phaseName,
        String content,
        UUID teacherId,
        @Valid @Size(max = 10) List<CourseSessionItemRequest> items
) {}
