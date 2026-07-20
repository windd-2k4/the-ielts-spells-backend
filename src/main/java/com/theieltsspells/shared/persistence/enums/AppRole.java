package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AppRole {
    ADMIN("admin"),
    CMS_EDITOR("cms_editor"),
    ADMISSIONS("admissions"),
    TEACHER("teacher"),
    TEACHING_ASSISTANT("teaching_assistant"),
    STUDENT("student");

    private final String databaseValue;
}
