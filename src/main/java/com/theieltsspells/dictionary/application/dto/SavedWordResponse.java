package com.theieltsspells.dictionary.application.dto;

import java.util.UUID;

public record SavedWordResponse(
        UUID id,
        UUID topicId,
        String term,
        String meaning,
        String masteryStatus
) {}
