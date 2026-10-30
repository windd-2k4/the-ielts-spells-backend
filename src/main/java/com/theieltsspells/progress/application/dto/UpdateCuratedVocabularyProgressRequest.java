package com.theieltsspells.progress.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateCuratedVocabularyProgressRequest(
        @NotBlank
        @Pattern(regexp = "NOT_LEARNED|FAMILIAR|MASTERED", message = "masteryStatus must be NOT_LEARNED, FAMILIAR, or MASTERED")
        String masteryStatus
) {}
