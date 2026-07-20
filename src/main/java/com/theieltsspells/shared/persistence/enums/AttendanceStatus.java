package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AttendanceStatus {
    PRESENT("present"),
    LATE("late"),
    LEFT_EARLY("left_early"),
    ABSENT("absent"),
    EXCUSED("excused");

    private final String databaseValue;
}
