package com.theieltsspells.assignment.application.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkAssignmentReminderRequest(
        @Size(max = 500) List<UUID> studentIds,
        @Size(max = 1000) String message,
        String channel,
        Boolean sendNotification
) {}
