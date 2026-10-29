package com.theieltsspells.testing.application.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BulkTestAssignmentReminderRequest(
        @NotNull
        UUID testAssignmentId,
        @Size(max = 500) List<UUID> studentIds,
        @Size(max = 1000) String message
) {
}
