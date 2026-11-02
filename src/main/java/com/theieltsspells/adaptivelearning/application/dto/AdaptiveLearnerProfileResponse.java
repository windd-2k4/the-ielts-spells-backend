package com.theieltsspells.adaptivelearning.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdaptiveLearnerProfileResponse(
        boolean enabled,
        UUID learnerId,
        List<KnowledgeState> knowledgeStates,
        List<Recommendation> recommendations
) {
    public record KnowledgeState(
            String componentCode,
            String componentName,
            String skill,
            BigDecimal masteryProbability,
            BigDecimal confidence,
            int evidenceCount,
            int correctCount,
            String modelVersion,
            OffsetDateTime lastEvidenceAt
    ) {
    }

    public record Recommendation(
            UUID id,
            String componentCode,
            String title,
            String reason,
            String status,
            UUID sourceAttemptId,
            String modelVersion,
            OffsetDateTime createdAt
    ) {
    }
}

