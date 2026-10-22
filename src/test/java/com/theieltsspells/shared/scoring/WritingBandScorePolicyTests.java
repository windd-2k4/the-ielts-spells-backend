package com.theieltsspells.shared.scoring;

import com.theieltsspells.shared.WritingBandScorePolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WritingBandScorePolicyTests {
    @Test
    void averagesFourCriteriaAndRoundsToNearestHalfBand() {
        assertEquals(new BigDecimal("6.5"), WritingBandScorePolicy.taskBand(bands("6.0", "6.5", "6.5", "6.0")));
    }

    @Test
    void givesTaskTwoTwiceTheWeightForAFullWritingTest() {
        assertEquals(new BigDecimal("7"), WritingBandScorePolicy.fullWritingBand(
                bands("6.0", "6.0", "6.0", "6.0"),
                bands("7.5", "7.5", "7.5", "7.5")));
    }

    private List<BigDecimal> bands(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }
}
