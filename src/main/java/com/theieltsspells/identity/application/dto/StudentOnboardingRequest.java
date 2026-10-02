package com.theieltsspells.identity.application.dto;

import jakarta.validation.constraints.Size;

public record StudentOnboardingRequest(
        @Size(max = 150, message = "Họ và tên không được vượt quá 150 ký tự")
        String fullName
) {}
