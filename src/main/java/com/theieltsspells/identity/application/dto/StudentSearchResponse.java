package com.theieltsspells.identity.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StudentSearchResponse(
        UUID id,
        String studentCode,
        String fullName,
        String email,
        String phone,
        String avatarPath,
        BigDecimal currentBand,
        BigDecimal targetBand
) {}
