package com.theieltsspells.testing.application.dto;

public record SpeakingAudioUploadResponse(
        String storageKey,
        String audioUrl,
        long sizeBytes,
        long durationMs
) {}
