package com.theieltsspells.testing.application.dto;

import java.time.OffsetDateTime;

public record WritingSavedResponse(
        String taskKey,
        String text,
        int wordCount,
        int clientRevision,
        OffsetDateTime answeredAt
) {
}
