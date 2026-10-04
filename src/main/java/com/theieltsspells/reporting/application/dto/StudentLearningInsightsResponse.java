package com.theieltsspells.reporting.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StudentLearningInsightsResponse(
        Summary summary,
        List<SkillInsight> skills,
        List<TestAttemptInsight> attempts,
        List<RecurringMistake> recurringMistakes
) {
    public record Summary(
            int totalAttempts,
            int completedAttempts,
            int inProgressAttempts,
            long totalStudyMinutes,
            Integer averageAccuracy,
            Integer averageScorePercent,
            OffsetDateTime lastActivityAt,
            String supportLevel,
            List<String> supportReasons
    ) {}

    public record SkillInsight(
            String skill,
            int attempts,
            int answeredQuestions,
            int correctAnswers,
            Integer accuracy,
            Integer averageScorePercent
    ) {}

    public record TestAttemptInsight(
            UUID id,
            String title,
            String skill,
            String status,
            String origin,
            String courseCode,
            String courseName,
            short attemptNo,
            OffsetDateTime startedAt,
            OffsetDateTime submittedAt,
            OffsetDateTime lastActivityAt,
            BigDecimal score,
            BigDecimal maxScore,
            int correctCount,
            int incorrectCount,
            int unansweredCount,
            Integer accuracy
    ) {}

    public record RecurringMistake(
            String questionType,
            String skill,
            int errorCount,
            int affectedAttempts,
            String recommendation
    ) {}
}
