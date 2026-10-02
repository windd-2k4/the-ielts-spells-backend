package com.theieltsspells.studentportal.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateStudentTargetBandRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal targetBand
) {
}
