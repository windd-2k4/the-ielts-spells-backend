package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LeadStatus {
    NEW("new"),
    CONTACTED("contacted"),
    QUALIFIED("qualified"),
    CONVERTED("converted"),
    LOST("lost");

    private final String databaseValue;
}
