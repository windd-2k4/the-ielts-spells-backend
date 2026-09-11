package com.theieltsspells.academic.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Replaces the complete Student Support roster for one course. */
public record ReplaceCourseStudentSupportsRequest(List<@NotNull UUID> studentSupportIds) {
    public ReplaceCourseStudentSupportsRequest {
        studentSupportIds = studentSupportIds == null ? List.of() : List.copyOf(studentSupportIds);
    }
}
