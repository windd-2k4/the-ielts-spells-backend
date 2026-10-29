package com.theieltsspells.progress.application.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateVocabularyMasteryRequest(
        @NotNull VocabularyMasteryStatus status
) {}

