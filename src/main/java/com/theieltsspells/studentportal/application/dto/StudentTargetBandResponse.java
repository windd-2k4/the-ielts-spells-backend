package com.theieltsspells.studentportal.application.dto;

import java.math.BigDecimal;

public record StudentTargetBandResponse(
        BigDecimal currentBand,
        BigDecimal targetBand,
        BigDecimal listeningTarget,
        BigDecimal readingTarget,
        BigDecimal writingTarget,
        BigDecimal speakingTarget
) {
}
