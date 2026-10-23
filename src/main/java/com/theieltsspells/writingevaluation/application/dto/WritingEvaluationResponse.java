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
        String studentCode,
        String testTitle,
        String attemptOrigin,
        String courseCode,
        String courseName,
        String courseSkillPair,
        String taskKey,
        String taskType,
        String essayText,
        String status,
        String teacherStatus,
        BigDecimal overallBandAi,
        BigDecimal overallBandTeacher,
        Map<String, Criterion> criteria,
        List<String> aiStrengths,
        List<String> aiImprovements,
        List<String> strengths,
        List<String> improvements,
        String errorMessage,
        OffsetDateTime aiCompletedAt,
        OffsetDateTime reviewedAt,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt,
        Map<String, Object> taskContext,
        String teacherEditedText,
        String teacherEditedHtml,
        List<WritingTeacherAnnotation> teacherAnnotations,
        Integer teacherRevision
) {
    public record Criterion(BigDecimal aiBand, BigDecimal teacherBand, String aiFeedback, String teacherFeedback) { }
}
