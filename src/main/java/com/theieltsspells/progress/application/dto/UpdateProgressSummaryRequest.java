package com.theieltsspells.progress.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateProgressSummaryRequest(
        @Min(0) Integer durationSeconds,
        @Min(0) @Max(100) Short comprehensionPercent
) {}
