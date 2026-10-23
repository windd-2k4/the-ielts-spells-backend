package com.theieltsspells.testing.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ReviewSpeakingEvaluationRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal overallBand,
        @NotNull @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal fluencyBand,
        @NotNull @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal lexicalBand,
        @NotNull @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal grammarBand,
        @NotNull @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal pronunciationBand,
        Map<String, String> criterionFeedback,
        String feedback,
        List<String> strengths,
        List<String> improvements,
        boolean publish
) { }
