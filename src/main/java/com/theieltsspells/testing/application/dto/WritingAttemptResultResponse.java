package com.theieltsspells.testing.application.dto;

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
        List<WritingTaskResponse> tasks,
        List<WritingSavedResponse> responses,
        List<Evaluation> evaluations
) {
    public record Evaluation(
            String taskKey,
            String status,
            BigDecimal overallBand,
            Map<String, BigDecimal> criterionBands,
            List<String> strengths,
            List<String> improvements,
            OffsetDateTime publishedAt
    ) {
    }
}
