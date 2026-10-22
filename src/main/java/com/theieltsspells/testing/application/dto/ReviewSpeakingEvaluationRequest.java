package com.theieltsspells.testing.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record ReviewSpeakingEvaluationRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal overallBand,
        @NotNull @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal fluencyBand,
        @NotNull @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal lexicalBand,
        @NotNull @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal grammarBand,
        @NotNull @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal pronunciationBand,
        String feedback,
        List<String> strengths,
        List<String> improvements,
        boolean publish
) { }
