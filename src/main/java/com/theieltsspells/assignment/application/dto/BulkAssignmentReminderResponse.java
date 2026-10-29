package com.theieltsspells.assignment.application.dto;

public record BulkAssignmentReminderResponse(
        int recipients,
        int notificationsCreated,
        int contactLogsCreated
) {}
