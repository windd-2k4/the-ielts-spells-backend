package com.theieltsspells.testing.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AdminSpeakingEvaluationResponse(
        UUID id,
        UUID attemptId,
        UUID studentId,
        String studentName,
        String testTitle,
        String questionKey,
        String audioUrl,
        String transcript,
        String aiStatus,
        BigDecimal overallBandAi,
        Map<String, Criterion> criteria,
        List<String> aiStrengths,
        List<String> aiImprovements,
        String teacherStatus,
        BigDecimal overallBandTeacher,
        String teacherFeedback,
        List<String> teacherStrengths,
        List<String> teacherImprovements,
        OffsetDateTime reviewedAt,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt
) {
    public record Criterion(BigDecimal aiBand, BigDecimal teacherBand, String aiFeedback) { }
}
