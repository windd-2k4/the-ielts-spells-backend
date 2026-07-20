package com.theieltsspells.academic.application.dto;

import com.theieltsspells.shared.persistence.enums.EnrollmentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateEnrollmentRequest(
        @NotNull EnrollmentStatus status,
        @Size(max = 2000) String notes
) {}
