package com.theieltsspells.academic.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BulkCreateSessionsRequest(
        @NotNull LocalDate startsOn,
        @Valid @NotEmpty @Size(max = 7) List<WeeklySlotRequest> weeklySlots,
        UUID teacherId,
        String zoomUrl,
        @Valid @NotEmpty List<ScheduleTemplateEntryRequest> entries
) {}
