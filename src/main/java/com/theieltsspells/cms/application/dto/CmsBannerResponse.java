package com.theieltsspells.cms.application.dto;

import com.theieltsspells.shared.persistence.enums.PublishStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CmsBannerResponse(
        UUID id, String title, String subtitle, String mediaPath, String targetUrl, String position,
        Integer displayOrder, OffsetDateTime startsAt, OffsetDateTime endsAt, PublishStatus status,
        UUID campaignId, UUID createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt
) {
}
