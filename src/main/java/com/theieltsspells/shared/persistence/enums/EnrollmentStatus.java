package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EnrollmentStatus {
    PENDING("pending"),
    ACTIVE("active"),
    PAUSED("paused"),
    COMPLETED("completed"),
    WITHDRAWN("withdrawn");

    private final String databaseValue;
}
