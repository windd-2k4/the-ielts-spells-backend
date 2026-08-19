package com.theieltsspells.learninglibrary.application.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MediaAssetResponse(
        UUID id, UUID resourceId, String code, String filename, String mimeType,
        String fileUrl, long sizeBytes, List<String> tags,
        List<UsageLocation> usedLocations, String uploadedBy, OffsetDateTime createdAt
) {
    public record UsageLocation(String type, String name, UUID id) {}
}
