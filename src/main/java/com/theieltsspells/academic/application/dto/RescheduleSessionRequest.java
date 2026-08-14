package com.theieltsspells.academic.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public record RescheduleSessionRequest(
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        boolean shiftFollowing,
        @Valid @NotEmpty @Size(max = 7) List<WeeklySlotRequest> weeklySlots
) {}
