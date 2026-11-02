package com.theieltsspells.adaptivelearning.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MasteryUpdatePolicyTests {

    @Test
    void movesMasteryTowardLatestEvidenceAndCapsConfidence() {
        assertThat(MasteryUpdatePolicy.update(new BigDecimal("0.5000"), true))
                .isEqualByComparingTo("0.6000");
        assertThat(MasteryUpdatePolicy.update(new BigDecimal("0.5000"), false))
                .isEqualByComparingTo("0.4000");
        assertThat(MasteryUpdatePolicy.confidence(3)).isEqualByComparingTo("0.3000");
        assertThat(MasteryUpdatePolicy.confidence(15)).isEqualByComparingTo("1.0000");
    }
}

