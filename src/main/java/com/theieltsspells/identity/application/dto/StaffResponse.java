package com.theieltsspells.identity.application.dto;

import com.theieltsspells.identity.domain.StaffStatus;
import com.theieltsspells.shared.persistence.enums.AppRole;

import java.time.*;
import java.util.UUID;

public record StaffResponse(UUID id, UUID authUserId, String fullName, String email, String phone,
                            String avatarPath, String jobTitle, String department, String employmentType,
                            LocalDate startDate, AppRole role, String cvPath, String portfolioUrl,
                            String professionalSummary, String internalNotes, StaffStatus status,
                            OffsetDateTime activatedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
