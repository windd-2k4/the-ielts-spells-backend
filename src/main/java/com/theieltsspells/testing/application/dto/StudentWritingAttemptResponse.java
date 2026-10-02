package com.theieltsspells.testing.application.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StudentWritingAttemptResponse(
        UUID attemptId,
        UUID assignmentId,
        UUID testVersionId,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime expiresAt,
        long remainingSeconds,
        String title,
        String description,
        boolean allowResultAfterSubmit,
        List<WritingTaskResponse> tasks,
        List<WritingSavedResponse> responses
) {
}
