package com.theieltsspells.testing.application.dto;

import java.util.List;
import java.util.UUID;

public record TestValidationResponse(
        UUID testId,
        int draftRevision,
        boolean publishable,
        List<TestValidationIssueResponse> issues
) {
}
