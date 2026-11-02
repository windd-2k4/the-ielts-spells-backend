package com.theieltsspells.adaptivelearning.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MasteryUpdatePolicy {
    private static final BigDecimal RETAINED_WEIGHT = new BigDecimal("0.8");
    private static final BigDecimal NEW_EVIDENCE_WEIGHT = new BigDecimal("0.2");

    private MasteryUpdatePolicy() {
    }

    public static BigDecimal update(BigDecimal current, boolean correct) {
        BigDecimal evidence = correct ? BigDecimal.ONE : BigDecimal.ZERO;
        return current.multiply(RETAINED_WEIGHT)
                .add(evidence.multiply(NEW_EVIDENCE_WEIGHT))
                .setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal confidence(int evidenceCount) {
        return BigDecimal.valueOf(Math.min(1.0, evidenceCount / 10.0))
                .setScale(4, RoundingMode.HALF_UP);
    }
}

