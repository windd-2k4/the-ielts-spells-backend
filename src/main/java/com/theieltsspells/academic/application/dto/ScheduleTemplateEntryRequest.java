package com.theieltsspells.academic.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ScheduleTemplateEntryRequest(
        @Positive short sessionNo,
        @NotBlank String entryType,
        String phaseName,
        @NotEmpty List<@NotBlank String> contents
) {}
