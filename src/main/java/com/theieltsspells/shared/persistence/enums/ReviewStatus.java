package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReviewStatus {
    NOT_REQUIRED("not_required"),
    PENDING("pending"),
    VERIFIED("verified"),
    REVISION_REQUESTED("revision_requested"),
    REJECTED("rejected");

    private final String databaseValue;
}
