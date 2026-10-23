package com.theieltsspells.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

/** Official IELTS Speaking overall: four equally weighted criteria, rounded to a half band. */
public final class SpeakingBandScorePolicy {
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal FOUR = BigDecimal.valueOf(4);

    private SpeakingBandScorePolicy() {}

    public static BigDecimal overall(Collection<BigDecimal> criterionBands) {
        if (criterionBands == null || criterionBands.size() != 4 || criterionBands.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Exactly four Speaking criterion bands are required");
        }
        criterionBands.forEach(SpeakingBandScorePolicy::validate);
        BigDecimal average = criterionBands.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(FOUR, 8, RoundingMode.HALF_UP);
        return average.multiply(TWO).setScale(0, RoundingMode.HALF_UP).divide(TWO).setScale(1);
    }

    private static void validate(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(9)) > 0
                || value.multiply(TWO).stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Speaking band must be between 0 and 9 in 0.5 steps");
        }
    }
}
