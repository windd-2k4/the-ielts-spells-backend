package com.theieltsspells.notification.application.dto;

public record UpdateStudentNotificationPreferencesRequest(
        Boolean upcomingClassReminder,
        Boolean assignmentDeadlineReminder,
        Boolean gradingCompletedAlert,
        Boolean weeklyProgressDigest,
        Boolean browserSoundAlert
) {}
