package com.theieltsspells.testing.application.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveReadingResponseItem(
        @NotBlank String questionKey,
        @NotNull JsonNode answer,
        @NotNull @Min(0) Integer clientRevision
) {
}
