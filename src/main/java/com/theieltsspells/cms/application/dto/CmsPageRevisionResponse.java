package com.theieltsspells.cms.application.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record CmsPageRevisionResponse(
        UUID id,
        UUID pageId,
        long revisionNo,
        String slug,
        String title,
        String excerpt,
        Map<String, Object> content,
        Map<String, Object> seoMetadata,
        String source,
        UUID createdBy,
        OffsetDateTime createdAt
) {
}
