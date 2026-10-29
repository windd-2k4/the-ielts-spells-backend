package com.theieltsspells.assignment.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

final class AssignmentSubmissionPolicy {
    private static final BigDecimal IELTS_MAX_SCORE = new BigDecimal("9");

    private AssignmentSubmissionPolicy() {
    }

    static BigDecimal maxScore(String skill, BigDecimal requested) {
        return "WRITING".equalsIgnoreCase(skill) || "SPEAKING".equalsIgnoreCase(skill)
                ? IELTS_MAX_SCORE
                : requested;
    }

    static OffsetDateTime effectiveDueAt(OffsetDateTime dueAt, int graceMinutes, OffsetDateTime extensionDueAt) {
        OffsetDateTime dueWithGrace = dueAt == null ? null : dueAt.plusMinutes(Math.max(0, graceMinutes));
        if (dueWithGrace == null) return extensionDueAt;
        if (extensionDueAt == null) return dueWithGrace;
        return dueWithGrace.isAfter(extensionDueAt) ? dueWithGrace : extensionDueAt;
    }
}
