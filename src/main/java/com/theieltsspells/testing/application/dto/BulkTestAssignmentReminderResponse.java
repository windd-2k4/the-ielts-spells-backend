package com.theieltsspells.testing.application.dto;

public record BulkTestAssignmentReminderResponse(
        int recipients,
        int notificationsCreated
) {
}
