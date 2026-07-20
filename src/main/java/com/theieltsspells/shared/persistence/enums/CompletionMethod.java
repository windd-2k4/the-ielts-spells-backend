package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CompletionMethod {
    WEB_ONLY("web_only"),
    MANUAL_ONLY("manual_only"),
    WEB_OR_MANUAL("web_or_manual");

    private final String databaseValue;
}
