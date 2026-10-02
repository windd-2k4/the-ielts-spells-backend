package com.theieltsspells.identity.application.dto;

import java.util.UUID;

public record StudentOnboardingResponse(
        UUID userId,
        String fullName,
        String studentCode
) {}
