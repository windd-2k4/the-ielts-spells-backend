package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PublishStatus {
    DRAFT("draft"),
    SCHEDULED("scheduled"),
    PUBLISHED("published"),
    ARCHIVED("archived");

    private final String databaseValue;
}
