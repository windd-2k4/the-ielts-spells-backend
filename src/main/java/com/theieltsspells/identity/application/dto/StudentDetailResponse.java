package com.theieltsspells.identity.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record StudentDetailResponse(
        UUID id,
        String studentCode,
        String fullName,
        String email,
        String phone,
        String avatarPath,
        BigDecimal currentBand,
        BigDecimal targetBand,
        LocalDate dateOfBirth,
        String address,
        Map<String, Object> emergencyContact,
        LocalDate joinedAt,
        String notes,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
