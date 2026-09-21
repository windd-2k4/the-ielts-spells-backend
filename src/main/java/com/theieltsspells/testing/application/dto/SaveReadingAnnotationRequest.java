package com.theieltsspells.testing.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveReadingAnnotationRequest(
        @NotBlank @Size(max = 120) String sectionKey,
        @NotBlank @Pattern(regexp = "HIGHLIGHT|NOTE|UNDERLINE|STRIKETHROUGH") String type,
        @NotBlank @Pattern(regexp = "YELLOW|GREEN|PINK|CYAN|RED|INK") String color,
        @NotNull @Min(0) Integer startOffset,
        @NotNull @Min(1) Integer endOffset,
        @NotBlank @Size(max = 5000) String selectedText,
        @Size(max = 200) String prefix,
        @Size(max = 200) String suffix,
        @Size(max = 2000) String note
) {
}
