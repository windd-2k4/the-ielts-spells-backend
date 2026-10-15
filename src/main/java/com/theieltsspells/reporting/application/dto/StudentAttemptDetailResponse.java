package com.theieltsspells.reporting.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StudentAttemptDetailResponse(
        UUID id,
        String title,
        String skill,
        String status,
        String origin,
        short attemptNo,
        OffsetDateTime startedAt,
        OffsetDateTime submittedAt,
        OffsetDateTime lastActivityAt,
        long durationSeconds,
        long allowedDurationSeconds,
        BigDecimal score,
        BigDecimal maxScore,
        int correctCount,
        int incorrectCount,
        int unansweredCount,
        List<QuestionDetail> questions
) {
    public record QuestionDetail(
            String questionKey,
            int questionNo,
            String questionType,
            String sectionTitle,
            String groupTitle,
            String prompt,
            List<String> studentAnswers,
            List<String> correctAnswers,
            Boolean correct,
            BigDecimal score,
            BigDecimal maxScore,
            String explanation
    ) {}
}
