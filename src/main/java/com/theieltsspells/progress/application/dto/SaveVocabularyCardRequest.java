package com.theieltsspells.progress.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SaveVocabularyCardRequest(
        UUID topicId,
        @NotBlank @Size(max = 200) String term,
        @Size(max = 1000) String meaning,
        @Size(max = 2000) String exampleSentence,
        @Size(max = 80) String partOfSpeech,
        @Size(max = 2000) String note,
        @Size(max = 250) String sourceLabel
) {}

