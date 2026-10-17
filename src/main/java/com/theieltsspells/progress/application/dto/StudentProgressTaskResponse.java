package com.theieltsspells.progress.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StudentProgressTaskResponse(
        UUID classActivityId,
        UUID sessionId,
        short sessionNo,
        String sessionTitle,
        UUID activityId,
        String title,
        String skill,
        String completionMethod,
        UUID attemptId,
        String source,
        String status,
        BigDecimal score,
        BigDecimal maxScore,
        Integer durationSeconds,
        Short comprehensionPercent,
        String errorAnalysis,
        int evidenceCount,
        List<ChecklistItem> checklist
) {
    public record ChecklistItem(UUID id, String label, String description, boolean required, boolean completed) {}
}
