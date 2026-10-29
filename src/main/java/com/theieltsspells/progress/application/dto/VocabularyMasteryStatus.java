package com.theieltsspells.progress.application.dto;

public enum VocabularyMasteryStatus {
    NOT_LEARNED((short) 0),
    FAMILIAR((short) 3),
    MASTERED((short) 5);

    private final short level;

    VocabularyMasteryStatus(short level) {
        this.level = level;
    }

    public short level() {
        return level;
    }

    public static VocabularyMasteryStatus fromLevel(short level) {
        if (level >= MASTERED.level) return MASTERED;
        if (level >= FAMILIAR.level) return FAMILIAR;
        return NOT_LEARNED;
    }
}

