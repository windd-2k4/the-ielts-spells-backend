package com.theieltsspells.testing.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveReadingResponsesRequest(
        @NotEmpty @Size(max = 50) List<@Valid SaveReadingResponseItem> responses
) {
}
