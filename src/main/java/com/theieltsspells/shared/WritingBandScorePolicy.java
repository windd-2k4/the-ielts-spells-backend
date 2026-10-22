package com.theieltsspells.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

public final class WritingBandScorePolicy {
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal FOUR = BigDecimal.valueOf(4);

    private WritingBandScorePolicy() { }

    public static BigDecimal taskBand(Collection<BigDecimal> criterionBands) {
        return roundToHalf(averageOfFour(criterionBands));
    }

    public static BigDecimal fullWritingBand(Collection<BigDecimal> taskOneCriteria,
                                             Collection<BigDecimal> taskTwoCriteria) {
        BigDecimal weighted = averageOfFour(taskOneCriteria)
                .add(averageOfFour(taskTwoCriteria).multiply(TWO))
                .divide(BigDecimal.valueOf(3), 8, RoundingMode.HALF_UP);
        return roundToHalf(weighted);
    }

    public static BigDecimal roundToHalf(BigDecimal value) {
        return value.multiply(TWO).setScale(0, RoundingMode.HALF_UP).divide(TWO);
    }

    private static BigDecimal averageOfFour(Collection<BigDecimal> values) {
        if (values == null || values.size() != 4 || values.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Exactly four Writing criterion bands are required");
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(FOUR, 8, RoundingMode.HALF_UP);
    }
}
