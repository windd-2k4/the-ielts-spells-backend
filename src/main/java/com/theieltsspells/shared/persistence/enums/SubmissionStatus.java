package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubmissionStatus {
    DRAFT("draft"),
    SUBMITTED("submitted"),
    LATE("late"),
    RETURNED("returned"),
    GRADED("graded");

    private final String databaseValue;
}
