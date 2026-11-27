package com.theieltsspells.testing.application.dto;

import com.theieltsspells.testing.domain.SpeakingSessionVariant;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record SpeakingEvaluationResponse(
        String questionKey,
        String status,
        SpeakingSessionVariant variant,
        List<Integer> partsEvaluated,
        boolean overallBandAvailable,
        Map<String, BigDecimal> criteria,
        BigDecimal overallBand,
        BigDecimal fluencyBand,
        BigDecimal lexicalBand,
        BigDecimal grammarBand,
        BigDecimal pronunciationBand,
        Map<String, String> criteriaFeedback,
        List<String> strengths,
        List<String> improvements,
        SpeakingAiAnalytics.SpeechMetrics speechMetrics,
        List<SpeakingAiAnalytics.AnswerUpgrade> answerUpgrades,
        OffsetDateTime evaluatedAt,
        String teacherStatus,
        BigDecimal overallBandTeacher,
        BigDecimal fluencyBandTeacher,
        BigDecimal lexicalBandTeacher,
        BigDecimal grammarBandTeacher,
        BigDecimal pronunciationBandTeacher,
        Map<String, String> teacherCriteriaFeedback,
        String teacherFeedback,
        List<String> teacherStrengths,
        List<String> teacherImprovements,
        OffsetDateTime teacherPublishedAt
) {}
