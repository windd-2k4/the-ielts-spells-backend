package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AssignmentStatus {
    DRAFT("draft"),
    PUBLISHED("published"),
    CLOSED("closed"),
    ARCHIVED("archived");

    private final String databaseValue;
}
