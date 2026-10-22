package com.theieltsspells.writingevaluation.application.dto;

import java.math.BigDecimal;
import java.util.Map;

public record WritingCalibrationResponse(
        String rubricVersion,
        long sampleSize,
        BigDecimal overallMeanAbsoluteError,
        BigDecimal withinHalfBandPercent,
        BigDecimal withinOneBandPercent,
        Map<String, Criterion> criteria
) {
    public record Criterion(
            long sampleSize,
            BigDecimal meanAbsoluteError,
            BigDecimal withinHalfBandPercent,
            BigDecimal withinOneBandPercent
    ) { }
}
