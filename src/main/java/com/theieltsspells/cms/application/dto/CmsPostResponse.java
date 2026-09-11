package com.theieltsspells.cms.application.dto;

import com.theieltsspells.shared.persistence.enums.PublishStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CmsPostResponse(
        UUID id, String slug, String title, String excerpt, Map<String, Object> content,
        String coverPath, List<String> tags, PublishStatus status, OffsetDateTime publishedAt,
        UUID authorId, OffsetDateTime createdAt, OffsetDateTime updatedAt
) {
}
