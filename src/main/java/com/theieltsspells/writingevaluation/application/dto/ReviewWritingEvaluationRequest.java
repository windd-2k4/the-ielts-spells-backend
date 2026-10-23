package com.theieltsspells.writingevaluation.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ReviewWritingEvaluationRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal overallBand,
        @NotEmpty Map<String, @NotNull @Valid CriterionReview> criteria,
        List<String> strengths,
        List<String> improvements,
        boolean publish,
        @Size(max = 50000) String editedText,
        @Size(max = 200000) String editedHtml,
        @Size(max = 500) List<@Valid WritingTeacherAnnotation> annotations
) {
    public ReviewWritingEvaluationRequest(BigDecimal overallBand,
                                          Map<String, CriterionReview> criteria,
                                          List<String> strengths,
                                          List<String> improvements,
                                          boolean publish) {
        this(overallBand, criteria, strengths, improvements, publish, null, null, List.of());
    }

    public record CriterionReview(
            @NotNull @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal band,
            String feedback
    ) { }
}
