package com.theieltsspells.progress.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AttemptChecklistResultResponse(UUID checklistItemId, boolean completed,
        OffsetDateTime completedAt, String note) {}
