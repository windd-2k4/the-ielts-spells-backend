package com.theieltsspells.progress.application.dto;

import java.util.List;
import java.util.UUID;

public record CuratedVocabularySessionResponse(
        UUID deckId,
        String deckName,
        int sessionSize,
        List<CuratedVocabularyItemResponse> items
) {}
