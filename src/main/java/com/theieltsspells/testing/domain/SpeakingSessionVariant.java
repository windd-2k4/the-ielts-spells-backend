package com.theieltsspells.testing.domain;

import java.util.List;

public enum SpeakingSessionVariant {
    FULL_MOCK(List.of(1, 2, 3), true),
    PART_1(List.of(1), false),
    PART_2(List.of(2), false),
    PART_3(List.of(3), false);

    private final List<Integer> partsIncluded;
    private final boolean overallBandAvailable;

    SpeakingSessionVariant(List<Integer> partsIncluded, boolean overallBandAvailable) {
        this.partsIncluded = partsIncluded;
        this.overallBandAvailable = overallBandAvailable;
    }

    public List<Integer> partsIncluded() {
        return partsIncluded;
    }

    public boolean overallBandAvailable() {
        return overallBandAvailable;
    }
}
