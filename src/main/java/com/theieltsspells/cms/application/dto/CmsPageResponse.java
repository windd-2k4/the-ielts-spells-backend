package com.theieltsspells.cms.application.dto;

import com.theieltsspells.shared.persistence.enums.PublishStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record CmsPageResponse(
        UUID id, String slug, String title, String excerpt, Map<String, Object> content,
        Map<String, Object> seoMetadata, PublishStatus status, OffsetDateTime publishedAt,
        UUID createdBy, UUID updatedBy, OffsetDateTime createdAt, OffsetDateTime updatedAt
) {
}
