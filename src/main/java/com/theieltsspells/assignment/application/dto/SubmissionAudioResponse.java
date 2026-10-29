package com.theieltsspells.assignment.application.dto;

import java.util.UUID;

public record SubmissionAudioResponse(
        UUID id,
        String promptKey,
        String promptTitle,
        int sortOrder,
        String originalName,
        String mimeType,
        long sizeBytes,
        String url
) {}
