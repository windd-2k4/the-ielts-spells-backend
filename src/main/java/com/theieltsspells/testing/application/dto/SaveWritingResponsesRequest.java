package com.theieltsspells.testing.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveWritingResponsesRequest(
        @NotEmpty @Size(max = 2) List<@Valid SaveWritingResponseItem> responses
) {
}
