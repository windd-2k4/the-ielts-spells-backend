package com.theieltsspells.testing.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SpeakingAiEntitlementResponse(
        UUID studentId, String studentCode, String studentName, String email, String mode,
        Integer monthlyLimit, long usedThisMonth, OffsetDateTime activeFrom,
        OffsetDateTime expiresAt, String source, String note
) {}
