package com.theieltsspells.cms.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record CmsPostRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*", message = "Slug chỉ gồm chữ thường, số và dấu gạch ngang") String slug,
        @NotBlank @Size(max = 220) String title,
        @Size(max = 500) String excerpt,
        @NotNull Map<String, Object> content,
        @Size(max = 500) String coverPath,
        List<@Size(max = 80) String> tags
) {
}
