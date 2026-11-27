package com.theieltsspells.studentportal.application.dto;

public record StudentPreferencesResponse(
        String headline,
        String biography,
        String language,
        boolean showProfileToClass,
        boolean showLeaderboardScore,
        boolean allowTeacherFeedbackShare,
        String defaultPracticeSkill,
        String examFontSize,
        boolean autoSaveAnswers,
        boolean countdownWarning,
        boolean focusMode
) {}
