package com.theieltsspells.academic.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransferResponse(
        UUID id, UUID sourceEnrollmentId, UUID targetClassId, UUID targetEnrollmentId,
        UUID reservationId, String status, String reason, BigDecimal feeAdjustment,
        OffsetDateTime requestedAt, OffsetDateTime approvedAt, String notes
) {}

