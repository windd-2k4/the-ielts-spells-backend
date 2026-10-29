package com.theieltsspells.progress.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
        List<ChecklistItem> checklist,
        UUID assignmentId,
        String assignmentTitle,
        String assignmentDescription,
        OffsetDateTime assignmentDueAt,
        String latePolicy,
        int lateGraceMinutes,
        boolean late,
        OffsetDateTime extensionDueAt,
        OffsetDateTime effectiveDueAt,
        boolean canEdit,
        boolean canSubmit,
        String lockReason,
        OffsetDateTime reflectionDueAt,
        boolean allowResubmission,
        UUID submissionId,
        boolean audioSubmitted,
        short attemptNo,
        String submissionText,
        String submissionStatus,
        String reflection,
        String reviewStatus,
        UUID feedbackId,
        String feedback,
        OffsetDateTime feedbackAt,
        List<AudioFile> audioFiles,
        List<Criterion> criteria,
        List<Annotation> annotations
) {
    public record ChecklistItem(UUID id, String label, String description, boolean required, boolean completed) {}
    public record AudioFile(UUID id, String promptKey, String promptTitle, int sortOrder,
                            String originalName, String url) {}
    public record Criterion(String criterion, BigDecimal score, String feedback, int sortOrder) {}
    public record Annotation(UUID id, int startOffset, int endOffset, String quotedText,
                             String comment, String suggestion, int sortOrder) {}
}
