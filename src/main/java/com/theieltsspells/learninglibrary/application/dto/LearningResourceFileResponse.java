package com.theieltsspells.learninglibrary.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LearningResourceFileResponse(
        UUID id,
        UUID resourceId,
        String fileRole,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        boolean previewSupported,
        OffsetDateTime createdAt
) {}
