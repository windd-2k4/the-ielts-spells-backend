package com.theieltsspells.adaptivelearning.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpsertAdaptiveContentMappingRequest(
        @NotBlank @Size(max = 160) String componentCode,
        @NotBlank @Pattern(regexp = "PRACTICE_FILTER|TEST_VERSION|EXERCISE_TEMPLATE|LEARNING_RESOURCE") String targetType,
        @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}") String targetId,
        @NotBlank @Size(max = 240) String title,
        @NotBlank @Size(max = 1000) @Pattern(regexp = "(/|https?://).+") String href,
        @Min(1) @Max(480) Integer durationMinutes,
        @Pattern(regexp = "EASY|MEDIUM|HARD") String difficulty,
        @Min(-100) @Max(100) Integer priority,
        Boolean active
) {
}
