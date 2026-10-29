package com.theieltsspells.assignment.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ReviewSubmissionRequest(
        @NotBlank String status,
        BigDecimal score,
        @Size(max = 6000) String feedback,
        @Size(max = 4000) String privateNote,
        @Size(max = 1000) String adjustmentReason,
        @Valid @Size(max = 5) List<ReviewCriterionRequest> criteria,
        @Valid @Size(max = 200) List<InlineAnnotationRequest> annotations,
        OffsetDateTime reflectionDueAt
) {}
