package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EvaluationStatus {
    QUEUED("queued"),
    PROCESSING("processing"),
    AI_COMPLETED("ai_completed"),
    TEACHER_REVIEWED("teacher_reviewed"),
    PUBLISHED("published"),
    FAILED("failed"),
    NOT_ELIGIBLE("not_eligible");

    private final String databaseValue;
}
