package com.theieltsspells.dictionary.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SaveDictionaryWordRequest(
        @NotBlank @Size(max = 200) String headword,
        @Size(max = 1000) String meaning,
        @Size(max = 2000) String exampleSentence,
        @Size(max = 80) String partOfSpeech,
        @Size(max = 120) String ipa,
        UUID topicId
) {}
