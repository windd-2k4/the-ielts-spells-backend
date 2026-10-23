package com.theieltsspells.shared.scoring;

import com.theieltsspells.shared.SpeakingBandScorePolicy;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpeakingBandScorePolicyTests {
    @Test
    void givesAllFourCriteriaEqualWeightAndRoundsToHalfBand() {
        assertThat(SpeakingBandScorePolicy.overall(List.of(
                new BigDecimal("6.0"), new BigDecimal("6.5"),
                new BigDecimal("7.0"), new BigDecimal("6.5"))))
                .isEqualByComparingTo("6.5");
    }

    @Test
    void rejectsBandsOutsideOfficialHalfSteps() {
        assertThatThrownBy(() -> SpeakingBandScorePolicy.overall(List.of(
                new BigDecimal("6.0"), new BigDecimal("6.25"),
                new BigDecimal("7.0"), new BigDecimal("6.5"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
