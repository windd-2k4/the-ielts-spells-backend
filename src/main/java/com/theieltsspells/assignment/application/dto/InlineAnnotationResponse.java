package com.theieltsspells.assignment.application.dto;

import java.util.UUID;

public record InlineAnnotationResponse(
        UUID id,
        int startOffset,
        int endOffset,
        String quotedText,
        String comment,
        String suggestion,
        int sortOrder
) {}
