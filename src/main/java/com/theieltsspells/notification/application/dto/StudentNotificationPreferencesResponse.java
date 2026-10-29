package com.theieltsspells.notification.application.dto;

public record StudentNotificationPreferencesResponse(
        boolean upcomingClassReminder,
        boolean assignmentDeadlineReminder,
        boolean gradingCompletedAlert,
        boolean weeklyProgressDigest,
        boolean browserSoundAlert
) {}
