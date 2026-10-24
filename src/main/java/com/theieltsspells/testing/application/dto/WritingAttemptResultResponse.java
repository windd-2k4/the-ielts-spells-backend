package com.theieltsspells.testing.application.dto;

import com.theieltsspells.writingevaluation.application.dto.WritingTeacherAnnotation;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WritingAttemptResultResponse(
        UUID attemptId,
        String status,
        OffsetDateTime submittedAt,
        boolean resultVisible,
        String attemptOrigin,
        boolean aiEligible,
        BigDecimal overallBandAi,
        BigDecimal overallBandTeacher,
        List<WritingTaskResponse> tasks,
        List<WritingSavedResponse> responses,
        List<Evaluation> evaluations
) {
    public record Evaluation(
            String taskKey,
            String aiStatus,
            String aiErrorMessage,
            BigDecimal overallBandAi,
            Map<String, BigDecimal> aiCriterionBands,
            List<String> aiStrengths,
            List<String> aiImprovements,
            OffsetDateTime aiCompletedAt,
            String teacherStatus,
            BigDecimal overallBandTeacher,
            Map<String, BigDecimal> teacherCriterionBands,
            List<String> teacherStrengths,
            List<String> teacherImprovements,
            OffsetDateTime teacherPublishedAt,
            String teacherEditedText,
            String teacherEditedHtml,
            List<WritingTeacherAnnotation> teacherAnnotations
    ) {
    }
}
