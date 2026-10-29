package com.theieltsspells.assignment.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentSubmissionPolicyTests {
    @Test
    void graceAndExtensionUseTheLatestEffectiveDeadline() {
        var dueAt = OffsetDateTime.parse("2026-10-27T10:00:00+07:00");

        assertThat(AssignmentSubmissionPolicy.effectiveDueAt(dueAt, 30, dueAt.plusMinutes(15)))
                .isEqualTo(dueAt.plusMinutes(30));
        assertThat(AssignmentSubmissionPolicy.effectiveDueAt(dueAt, 30, dueAt.plusHours(2)))
                .isEqualTo(dueAt.plusHours(2));
    }

    @Test
    void speakingAndWritingAlwaysUseTheIeltsBandScale() {
        assertThat(AssignmentSubmissionPolicy.maxScore("WRITING", new BigDecimal("100")))
                .isEqualByComparingTo("9");
        assertThat(AssignmentSubmissionPolicy.maxScore("SPEAKING", null))
                .isEqualByComparingTo("9");
        assertThat(AssignmentSubmissionPolicy.maxScore("READING", new BigDecimal("40")))
                .isEqualByComparingTo("40");
    }
}
