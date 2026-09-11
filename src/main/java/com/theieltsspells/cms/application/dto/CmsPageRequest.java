package com.theieltsspells.cms.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CmsPageRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*", message = "Slug chỉ gồm chữ thường, số và dấu gạch ngang") String slug,
        @NotBlank @Size(max = 220) String title,
        @Size(max = 500) String excerpt,
        @NotNull Map<String, Object> content,
        Map<String, Object> seoMetadata
) {
}
