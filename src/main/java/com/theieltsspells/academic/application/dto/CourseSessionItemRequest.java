package com.theieltsspells.academic.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CourseSessionItemRequest(
        @NotBlank
        @Pattern(regexp = "ASSIGNMENT|TEST")
        String itemType,
        @NotBlank String title,
        String description,
        UUID sourceAssignmentId,
        UUID sourceTestId,
        OffsetDateTime deadlineAt
) {}
