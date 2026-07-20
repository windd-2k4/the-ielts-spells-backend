package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AttemptStatus {
    IN_PROGRESS("in_progress"),
    SUBMITTED("submitted"),
    GRADED("graded"),
    EXPIRED("expired");

    private final String databaseValue;
}
