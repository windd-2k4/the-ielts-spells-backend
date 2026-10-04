package com.theieltsspells.reporting.application;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class StudentLearningInsightsServiceTests {

    private final OffsetDateTime now = OffsetDateTime.parse("2026-10-04T09:00:00+07:00");

    @Test
    void classifiesSupportFromRealActivitySignals() {
        var noData = StudentLearningInsightsService.assessSupport(0, 0, null, null, false, now);
        var attention = StudentLearningInsightsService.assessSupport(5, 1, 44, now.minusDays(3), true, now);
        var onTrack = StudentLearningInsightsService.assessSupport(8, 0, 82, now.minusDays(2), false, now);

        assertThat(noData.level()).isEqualTo("NOT_ENOUGH_DATA");
        assertThat(attention.level()).isEqualTo("NEEDS_ATTENTION");
        assertThat(attention.reasons()).hasSize(2);
        assertThat(onTrack.level()).isEqualTo("ON_TRACK");
        assertThat(onTrack.reasons()).isEmpty();
    }
}
