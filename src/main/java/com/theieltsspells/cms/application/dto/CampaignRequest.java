package com.theieltsspells.cms.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CampaignRequest(
        @NotBlank @Size(max = 220) String name,
        @Size(max = 100) String source,
        @Size(max = 100) String medium,
        @Size(max = 100) String campaignCode,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        @DecimalMin("0.0") BigDecimal budget,
        Boolean active
) {
}
