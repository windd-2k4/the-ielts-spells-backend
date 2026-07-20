package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QuestionType {
    SINGLE_CHOICE("single_choice"),
    MULTIPLE_CHOICE("multiple_choice"),
    SHORT_TEXT("short_text"),
    LONG_TEXT("long_text"),
    WRITING("writing");

    private final String databaseValue;
}
