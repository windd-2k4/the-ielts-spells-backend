package com.theieltsspells.dictionary.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TranslateTextRequest(
        @NotBlank @Size(max = 3000) String text,
        String sourceLang,
        String targetLang,
        boolean includeIeltsTip
) {}
