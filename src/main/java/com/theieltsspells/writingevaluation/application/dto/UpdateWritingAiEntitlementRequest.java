package com.theieltsspells.writingevaluation.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record UpdateWritingAiEntitlementRequest(
        @NotNull Mode mode,
        @Min(1) Integer monthlyLimit,
        OffsetDateTime activeFrom,
        OffsetDateTime expiresAt,
        String note
) {
    public enum Mode { DISABLED, LIMITED, UNLIMITED }
}
