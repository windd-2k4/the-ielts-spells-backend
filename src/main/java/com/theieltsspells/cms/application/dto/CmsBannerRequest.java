package com.theieltsspells.cms.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CmsBannerRequest(
        @NotBlank @Size(max = 220) String title,
        @Size(max = 500) String subtitle,
        @Size(max = 500) String mediaPath,
        @Size(max = 500) String targetUrl,
        @NotBlank @Size(max = 100) String position,
        Integer displayOrder,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        UUID campaignId
) {
}
