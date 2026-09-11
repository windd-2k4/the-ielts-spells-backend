package com.theieltsspells.progress.application.dto;

import com.theieltsspells.shared.persistence.enums.ActivityAttemptStatus;
import com.theieltsspells.shared.persistence.enums.ResultSource;
import com.theieltsspells.shared.persistence.enums.ReviewStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ActivityAttemptResponse(UUID id, UUID studentId, Short attemptNo, ResultSource source,
        ActivityAttemptStatus status, ReviewStatus reviewStatus, BigDecimal score, BigDecimal maxScore,
        Integer correctCount, Integer incorrectCount, Integer unansweredCount, Integer durationSeconds,
        Short comprehensionPercent, String errorAnalysis, String improvementPlan,
        OffsetDateTime submittedAt, OffsetDateTime completedAt, boolean late) {}
