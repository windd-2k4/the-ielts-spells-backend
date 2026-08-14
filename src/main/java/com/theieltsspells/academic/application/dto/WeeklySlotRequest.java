package com.theieltsspells.academic.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record WeeklySlotRequest(
        @Min(1) @Max(7) int dayOfWeek,
        @NotNull LocalTime startsAt,
        @NotNull LocalTime endsAt
) {}
