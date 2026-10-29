package com.theieltsspells.assignment.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InlineAnnotationRequest(
        @Min(0) int startOffset,
        @Min(1) int endOffset,
        @NotBlank @Size(max = 2000) String quotedText,
        @NotBlank @Size(max = 2000) String comment,
        @Size(max = 2000) String suggestion,
        int sortOrder
) {}
