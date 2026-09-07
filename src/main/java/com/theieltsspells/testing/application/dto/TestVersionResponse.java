package com.theieltsspells.testing.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TestVersionResponse(
        UUID id,
        int versionNumber,
        String versionLabel,
        OffsetDateTime publishedAt,
        String publishedBy
) {
}
