package com.theieltsspells.writingevaluation.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ReviewWritingEvaluationRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal overallBand,
        @NotEmpty Map<String, @Valid CriterionReview> criteria,
        List<String> strengths,
        List<String> improvements,
        boolean publish
) {
    public record CriterionReview(
            @NotNull @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal band,
            String feedback
    ) { }
}
