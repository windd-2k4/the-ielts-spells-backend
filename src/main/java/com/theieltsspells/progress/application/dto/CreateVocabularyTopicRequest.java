package com.theieltsspells.progress.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVocabularyTopicRequest(
        @NotBlank @Size(max = 80) String name
) {}

