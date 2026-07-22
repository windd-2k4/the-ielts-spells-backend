package com.theieltsspells.academic.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferRequest(
        @NotNull UUID targetClassId,
        @NotBlank String reason,
        BigDecimal feeAdjustment,
        UUID reservationId,
        String notes
) {}

