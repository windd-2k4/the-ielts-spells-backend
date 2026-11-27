package com.theieltsspells.studentportal.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateStudentPreferencesRequest(
        @Size(max = 120) String headline,
        @Size(max = 1000) String biography,
        @NotNull @Pattern(regexp = "vi|en") String language,
        @NotNull Boolean showProfileToClass,
        @NotNull Boolean showLeaderboardScore,
        @NotNull Boolean allowTeacherFeedbackShare,
        @NotNull @Pattern(regexp = "READING|LISTENING|WRITING|SPEAKING") String defaultPracticeSkill,
        @NotNull @Pattern(regexp = "sm|md|lg") String examFontSize,
        @NotNull Boolean autoSaveAnswers,
        @NotNull Boolean countdownWarning,
        @NotNull Boolean focusMode
) {}
