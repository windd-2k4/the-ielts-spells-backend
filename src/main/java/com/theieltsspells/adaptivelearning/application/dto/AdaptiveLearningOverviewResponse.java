package com.theieltsspells.adaptivelearning.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdaptiveLearningOverviewResponse(
        boolean enabled,
        long learnersWithEvidence,
        long processedEvents,
        long activeRecommendations,
        long acceptedRecommendations,
        int knowledgeComponents,
        int mappedQuestionTypes,
        int activeContentMappings,
        List<RecentLearner> recentLearners
) {
    public record RecentLearner(
            UUID learnerId,
            String weakestComponentCode,
            String weakestComponentName,
            BigDecimal masteryProbability,
            BigDecimal confidence,
            int evidenceCount,
            OffsetDateTime lastEvidenceAt,
            String activeRecommendationTitle
    ) {
    }
}

