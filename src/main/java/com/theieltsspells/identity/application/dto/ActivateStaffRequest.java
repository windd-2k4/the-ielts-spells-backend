package com.theieltsspells.identity.application.dto;

import jakarta.validation.constraints.Size;

public record ActivateStaffRequest(
        @Size(max = 200) String fullName,
        @Size(max = 30) String phone,
        @Size(max = 1000) String avatarPath,
        @Size(max = 4000) String professionalSummary) {}
