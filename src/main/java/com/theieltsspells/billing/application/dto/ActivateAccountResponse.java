package com.theieltsspells.billing.application.dto;

import java.util.UUID;

public record ActivateAccountResponse(
        boolean success,
        String message,
        UUID userId,
        String email,
        String fullName,
        UUID enrolledCourseId,
        String redirectUrl
) {}
