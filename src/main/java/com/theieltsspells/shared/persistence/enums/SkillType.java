package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SkillType {
    LISTENING("listening"),
    READING("reading"),
    WRITING("writing"),
    SPEAKING("speaking"),
    VOCABULARY("vocabulary"),
    GENERAL("general");

    private final String databaseValue;
}
