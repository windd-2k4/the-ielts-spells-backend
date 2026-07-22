package com.theieltsspells.academic.application.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateReservationRequest(
        @NotBlank String reason,
        @NotNull @PositiveOrZero Short sessionsConsumed,
        @NotNull @PositiveOrZero Short sessionsRemaining,
        @NotNull @PositiveOrZero BigDecimal creditAmount,
        @Future LocalDate expiresOn,
        String notes
) {}

