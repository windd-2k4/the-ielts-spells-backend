package com.theieltsspells.testing.application.dto;

public record WritingTaskResponse(
        String taskKey,
        int taskNo,
        String title,
        String promptHtml,
        String imageUrl,
        String imageAltText,
        int minWords,
        int suggestedTimeMinutes,
        String responseMode
) {
}
