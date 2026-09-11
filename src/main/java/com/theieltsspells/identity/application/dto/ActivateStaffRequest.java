package com.theieltsspells.identity.application.dto;

import jakarta.validation.constraints.Size;

public record ActivateStaffRequest(
        @Size(max = 200) String fullName) {}
