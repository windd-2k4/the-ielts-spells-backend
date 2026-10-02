package com.theieltsspells.billing.application.dto;

import java.util.UUID;

public record VerifyActivationTokenResponse(
        boolean valid,
        String customerName,
        String customerEmail,
        UUID courseId,
        String courseTitle,
        String message
) {}
