package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResultSource {
    WEB("web"),
    STUDENT_MANUAL("student_manual"),
    TEACHER_MANUAL("teacher_manual"),
    IMPORTED("imported");

    private final String databaseValue;
}
