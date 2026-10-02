package com.theieltsspells.testing.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReadingAttemptResultResponse(
        UUID attemptId,
        String status,
        OffsetDateTime submittedAt,
        OffsetDateTime expiresAt,
        BigDecimal autoScore,
        BigDecimal maxScore,
        int correctCount,
        int incorrectCount,
        int unansweredCount,
        boolean resultVisible,
        List<QuestionResult> questions
) {
    public record QuestionResult(
            String questionKey,
            int questionNo,
            boolean answered,
            Boolean correct,
            BigDecimal score,
            BigDecimal maxScore,
            List<String> correctAnswers,
            String explanation,
            QuestionSolution solution,
            List<EvidenceSpan> evidenceSpans,
            EvidenceSpan evidenceSpan
    ) {
    }

    public record QuestionSolution(
            String explanation,
            List<String> reasoningSteps,
            String trapAnalysis,
            String vocabularyNotes,
            String relatedLessonUrl
        ) {
    }

    public record EvidenceSpan(
            UUID id,
            Integer start,
            Integer end,
            String quote,
            String prefix,
            String suffix,
            String paragraphKey,
            String label,
            ReadingEvidenceMode mode
    ) {
    }
}
