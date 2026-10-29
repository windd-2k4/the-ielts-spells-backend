package com.theieltsspells.testing.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TestAttemptInsightsResponse(
        List<QuestionTypeInsight> questionTypes,
        List<ReasonInsight> reasons,
        List<AttemptTrend> trends,
        int pendingReviews,
        List<ListeningPartInsight> listeningParts
) {
    public record QuestionTypeInsight(
            String type,
            int wrongCount,
            int answeredCount,
            int reviewedCount
    ) {
    }

    public record ReasonInsight(
            String reasonCode,
            int count
    ) {
    }

    public record AttemptTrend(
            UUID studentId,
            String studentCode,
            String studentName,
            String testTitle,
            short attemptNo,
            BigDecimal score,
            BigDecimal maxScore,
            OffsetDateTime submittedAt
    ) {
    }

    public record ListeningPartInsight(
            int partNo,
            String title,
            int wrongCount,
            int answeredCount,
            int reviewedCount
    ) {
    }
}
