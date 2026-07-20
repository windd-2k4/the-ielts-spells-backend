package com.theieltsspells.shared.persistence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ActivityType {
    ONLINE_TEST("online_test"),
    MANUAL_RESULT("manual_result"),
    WRITING_SUBMISSION("writing_submission"),
    AUDIO_SUBMISSION("audio_submission"),
    FILE_SUBMISSION("file_submission"),
    VOCABULARY_LIST("vocabulary_list"),
    CHECKLIST("checklist"),
    EXTERNAL_LINK("external_link");

    private final String databaseValue;
}
