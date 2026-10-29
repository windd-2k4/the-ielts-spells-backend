package com.theieltsspells.progress.application.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VocabularyMasteryStatusTests {
    @Test
    void mapsStoredLevelsToTheThreeLearnerFacingStates() {
        assertThat(VocabularyMasteryStatus.fromLevel((short) 0)).isEqualTo(VocabularyMasteryStatus.NOT_LEARNED);
        assertThat(VocabularyMasteryStatus.fromLevel((short) 3)).isEqualTo(VocabularyMasteryStatus.FAMILIAR);
        assertThat(VocabularyMasteryStatus.fromLevel((short) 5)).isEqualTo(VocabularyMasteryStatus.MASTERED);
    }
}
