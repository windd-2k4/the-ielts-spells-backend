package com.theieltsspells.writingevaluation.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WritingEvaluationResponse(
        UUID id,
        UUID attemptId,
        UUID studentId,
        String studentName,
        String testTitle,
        String taskKey,
        String taskType,
        String essayText,
        String status,
        BigDecimal overallBandAi,
        BigDecimal overallBandTeacher,
        Map<String, Criterion> criteria,
        List<String> strengths,
        List<String> improvements,
        String errorMessage,
        OffsetDateTime reviewedAt,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt
) {
    public record Criterion(BigDecimal aiBand, BigDecimal teacherBand, String aiFeedback, String teacherFeedback) { }
}
