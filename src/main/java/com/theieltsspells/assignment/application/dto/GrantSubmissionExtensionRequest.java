package com.theieltsspells.assignment.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record GrantSubmissionExtensionRequest(
        @NotNull OffsetDateTime extensionDueAt,
        @Size(max = 1000) String reason
) {}
