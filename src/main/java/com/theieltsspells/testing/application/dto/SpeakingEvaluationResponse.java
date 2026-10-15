package com.theieltsspells.testing.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record SpeakingEvaluationResponse(
        String questionKey,
        String status,
        BigDecimal overallBand,
        BigDecimal fluencyBand,
        BigDecimal lexicalBand,
        BigDecimal grammarBand,
        BigDecimal pronunciationBand,
        Map<String, String> criteriaFeedback,
        List<String> strengths,
        List<String> improvements,
        OffsetDateTime evaluatedAt
) {}
