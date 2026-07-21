package com.theieltsspells.identity.application.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateStaffRequest(
        @Size(min = 2, max = 150) String fullName,
        @Size(max = 30) String phone,
        @Size(max = 120) String jobTitle,
        @Size(max = 120) String department,
        @Size(max = 50) String employmentType,
        LocalDate startDate,
        @Size(max = 500) String cvPath,
        @Size(max = 500) String portfolioUrl,
        @Size(max = 3000) String professionalSummary,
        @Size(max = 3000) String internalNotes) {}
