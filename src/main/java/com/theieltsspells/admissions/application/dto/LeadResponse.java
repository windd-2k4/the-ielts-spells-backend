package com.theieltsspells.admissions.application.dto;

import com.theieltsspells.shared.persistence.enums.LeadStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LeadResponse(
        UUID id,
        String fullName,
        String phone,
        String email,
        BigDecimal currentBand,
        BigDecimal targetBand,
        UUID interestedCourseId,
        OffsetDateTime preferredContactAt,
        String source,
        LeadStatus status,
        UUID convertedStudentId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
