package com.theieltsspells.identity.application.dto;

import com.theieltsspells.shared.persistence.enums.AppRole;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record CreateStaffInvitationRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 30) String phone,
        @Size(max = 1000) String avatarPath,
        @Size(max = 150) String jobTitle,
        @Size(max = 150) String department,
        @Size(max = 50) String employmentType,
        LocalDate startDate,
        @NotNull AppRole role,
        @Size(max = 1000) String cvPath,
        @Size(max = 1000) String portfolioUrl,
        @Size(max = 4000) String professionalSummary,
        @Size(max = 4000) String internalNotes) {}
