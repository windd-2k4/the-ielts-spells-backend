package com.theieltsspells.academic.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReservationResponse(
        UUID id, UUID enrollmentId, String status, String reason,
        Short sessionsConsumed, Short sessionsRemaining, BigDecimal creditAmount,
        LocalDate expiresOn, UUID targetCourseId, OffsetDateTime requestedAt,
        OffsetDateTime approvedAt, String notes
) {}
