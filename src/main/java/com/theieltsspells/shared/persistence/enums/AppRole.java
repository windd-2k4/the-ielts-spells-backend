package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AppRole {
    ADMIN("admin"),
    ADMISSIONS("admissions"),
    SOCIAL_MEDIA("social_media"),
    TEACHER("teacher"),
    STUDENT_SUPPORT("student_support"),
    STUDENT("student"),

    /**
     * Transitional role. Existing holders must be reviewed and moved to ADMIN
     * or STUDENT_SUPPORT before this value can be removed from PostgreSQL.
     */
    @Deprecated
    MANAGER("manager"),

    /**
     * Transitional role. V023 migrates existing assignments to SOCIAL_MEDIA;
     * this value remains readable for an unexpired legacy JWT.
     */
    @Deprecated
    CMS_EDITOR("cms_editor"),

    /**
     * Transitional role. Its holders require a manual decision between
     * TEACHER and STUDENT_SUPPORT.
     */
    @Deprecated
    TEACHING_ASSISTANT("teaching_assistant");

    private final String databaseValue;
}
