package com.theieltsspells.dictionary.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LookupHistoryItemResponse(
        UUID id,
        String queryText,
        String direction,
        String entryType,
        String summary,
        OffsetDateTime createdAt
) {}
