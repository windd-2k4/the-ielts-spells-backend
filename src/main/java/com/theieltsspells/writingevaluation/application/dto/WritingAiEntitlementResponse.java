package com.theieltsspells.writingevaluation.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WritingAiEntitlementResponse(
        UUID studentId,
        String studentCode,
        String studentName,
        String email,
        String mode,
        Integer monthlyLimit,
        long usedThisMonth,
        OffsetDateTime activeFrom,
        OffsetDateTime expiresAt,
        String source,
        String note
) { }
