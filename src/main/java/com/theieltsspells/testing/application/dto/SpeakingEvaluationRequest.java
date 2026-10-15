package com.theieltsspells.testing.application.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record SpeakingEvaluationRequest(
        @NotBlank String questionKey,
        String questionPrompt,
        String audioUrl,
        @NotBlank String transcript
) {}
