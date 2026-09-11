package com.theieltsspells.cms.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CampaignResponse(
        UUID id, String name, String source, String medium, String campaignCode,
        OffsetDateTime startsAt, OffsetDateTime endsAt, BigDecimal budget, Boolean active,
        UUID createdBy, OffsetDateTime createdAt
) {
}
