package com.theieltsspells.testing.application.dto;

public record TestValidationIssueResponse(
        String id,
        String severity,
        String sectionTitle,
        Integer questionNo,
        String message,
        String targetId
) {
}
