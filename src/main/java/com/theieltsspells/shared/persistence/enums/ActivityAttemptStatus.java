package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ActivityAttemptStatus {
    NOT_STARTED("not_started"),
    IN_PROGRESS("in_progress"),
    SUBMITTED("submitted"),
    LATE("late"),
    WAITING_REVIEW("waiting_review"),
    NEEDS_REVISION("needs_revision"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    private final String databaseValue;
}
