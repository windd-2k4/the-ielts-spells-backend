package com.theieltsspells.aimentor.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AiMentorChatRequest(
        String mentorId,
        String accent,
        String mode,
        String cefrLevel,
        String learningGoal,
        @NotBlank @Size(max = 1200) String userMessage,
        List<String> weakWords,
        String storyTitle,
        String storySummary,
        List<HistoryTurn> history
) {
    public record HistoryTurn(String role, String text) {}
}
