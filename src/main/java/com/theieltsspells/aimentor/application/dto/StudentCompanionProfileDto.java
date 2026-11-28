package com.theieltsspells.aimentor.application.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record StudentCompanionProfileDto(
        @NotBlank String mascotType,
        @NotBlank String accent,
        @NotBlank String cefrLevel,
        @NotBlank String learningGoal,
        int dailyTargetXp,
        List<String> favoriteCategories,
        String preferredGame,
        boolean onboardingCompleted
) {}
