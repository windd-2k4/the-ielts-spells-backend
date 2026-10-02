package com.theieltsspells.testing.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReadingAnnotationResponse(
        UUID id,
        String sectionKey,
        String type,
        String color,
        int startOffset,
        int endOffset,
        String selectedText,
        String prefix,
        String suffix,
        String note,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
