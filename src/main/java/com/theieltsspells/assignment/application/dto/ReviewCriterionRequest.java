package com.theieltsspells.assignment.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ReviewCriterionRequest(
        @NotBlank @Size(max = 64) String criterion,
        @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal score,
        @Size(max = 2000) String feedback,
        int sortOrder
) {}
