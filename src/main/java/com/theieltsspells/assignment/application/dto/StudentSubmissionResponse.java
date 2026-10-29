package com.theieltsspells.assignment.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StudentSubmissionResponse(
        UUID id,
        UUID assignmentId,
        UUID classActivityId,
        UUID studentId,
        String studentName,
        short attemptNo,
        String textContent,
        String status,
        OffsetDateTime submittedAt,
        boolean late,
        BigDecimal score,
        String reviewStatus,
        String reflection,
        UUID feedbackId,
        String feedback,
        OffsetDateTime feedbackAt,
        String audioUrl,
        List<SubmissionAudioResponse> audioFiles,
        List<ReviewCriterionResponse> criteria,
        List<InlineAnnotationResponse> annotations,
        OffsetDateTime extensionDueAt,
        String extensionReason,
        OffsetDateTime reflectionDueAt
) {}
