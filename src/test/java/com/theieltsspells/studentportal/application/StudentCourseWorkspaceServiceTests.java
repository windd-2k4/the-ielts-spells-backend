package com.theieltsspells.studentportal.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudentCourseWorkspaceServiceTests {
    @Test
    void courseWorkspaceOnlyAcceptsSkillsFromItsSkillPair() {
        assertThat(StudentCourseWorkspaceService.skillAllowed("SPEAKING_WRITING", "WRITING")).isTrue();
        assertThat(StudentCourseWorkspaceService.skillAllowed("SPEAKING_WRITING", "READING")).isFalse();
        assertThat(StudentCourseWorkspaceService.skillAllowed("LISTENING_READING", "READING")).isTrue();
        assertThat(StudentCourseWorkspaceService.skillAllowed("LISTENING_READING", "SPEAKING")).isFalse();
    }
}
