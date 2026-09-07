package com.theieltsspells.testing.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TestRevisionRequest(
        @NotNull @Positive Integer draftRevision
) {
}
