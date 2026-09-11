package com.theieltsspells.testing.application.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateTestAssignmentRequest(
        @NotNull UUID testVersionId,
        @NotNull UUID courseId,
        OffsetDateTime opensAt,
        OffsetDateTime closesAt,
        @NotNull @Min(1) Short maxAttempts,
        @NotBlank @Pattern(regexp = "PRACTICE|EXAM", message = "mode phải là PRACTICE hoặc EXAM") String mode,
        @Min(1) Integer durationSeconds,
        Boolean showResultAfterSubmit
) {
}
