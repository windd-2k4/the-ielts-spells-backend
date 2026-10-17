package com.theieltsspells.testing.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpeakingVocabLookupRequest(
        @NotBlank @Size(max = 50, message = "Từ hoặc cụm từ tối đa 50 ký tự")
        String query,

        @NotBlank
        String direction // "EN_VI" | "VI_EN"
) {}
