package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SessionStatus {
    SCHEDULED("scheduled"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    private final String databaseValue;
}
