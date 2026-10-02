package com.theieltsspells.testing.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveWritingResponseItem(
        @NotBlank String taskKey,
        @NotNull String text,
        @NotNull @Min(0) Integer clientRevision
) {
}
