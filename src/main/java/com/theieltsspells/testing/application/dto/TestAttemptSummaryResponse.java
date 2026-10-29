package com.theieltsspells.testing.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TestAttemptSummaryResponse(
        UUID attemptId,
        UUID assignmentId,
        UUID testVersionId,
        UUID courseId,
        UUID sessionId,
        Short sessionNo,
        String sessionTitle,
        UUID studentId,
        String studentCode,
        String studentName,
        String testTitle,
        String versionLabel,
        String skill,
        short attemptNo,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime submittedAt,
        long durationSeconds,
        BigDecimal score,
        BigDecimal maxScore,
        int correctCount,
        int incorrectCount,
        int unansweredCount
) {
}
