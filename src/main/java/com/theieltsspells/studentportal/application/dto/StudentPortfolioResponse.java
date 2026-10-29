package com.theieltsspells.studentportal.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StudentPortfolioResponse(
        UUID courseId,
        String courseCode,
        String courseName,
        String skillPair,
        OffsetDateTime generatedAt,
        Summary summary,
        List<CriterionProgress> criterionProgress,
        List<TimelineEntry> timeline
) {
    public record Summary(
            int totalAssignments,
            int completedAssignments,
            int totalSubmissions,
            int gradedSubmissions,
            int revisionRequests,
            int reflections
    ) {}

    public record CriterionProgress(
            String skill,
            String criterion,
            String label,
            BigDecimal averageScore,
            BigDecimal latestScore,
            int gradedCount
    ) {}

    public record TimelineEntry(
            UUID assignmentId,
            UUID classActivityId,
            UUID submissionId,
            UUID attemptId,
            short attemptNo,
            int sessionNo,
            String sessionTitle,
            String assignmentTitle,
            String skill,
            String submissionStatus,
            String attemptStatus,
            String reviewStatus,
            OffsetDateTime submittedAt,
            BigDecimal score,
            BigDecimal maxScore,
            boolean late,
            OffsetDateTime extensionDueAt,
            String feedback,
            OffsetDateTime reviewedAt,
            String reflection,
            OffsetDateTime reflectionDueAt,
            boolean audioSubmitted,
            List<Criterion> rubric,
            List<Annotation> annotations
    ) {}

    public record Criterion(
            String criterion,
            String label,
            BigDecimal score,
            String feedback,
            int sortOrder
    ) {}

    public record Annotation(
            UUID id,
            int startOffset,
            int endOffset,
            String quotedText,
            String comment,
            String suggestion,
            int sortOrder
    ) {}
}
