package com.theieltsspells.progress.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CuratedVocabularyItemResponse(
        UUID id,
        UUID deckId,
        String term,
        String partOfSpeech,
        String phonetic,
        String meaning,
        String exampleSentence,
        String exampleSentenceVi,
        String chunk,
        String collocationsJson,
        String exerciseJson,
        String tip,
        String drillsJson,
        int orderIndex,
        String masteryStatus,
        int reviewCount,
        OffsetDateTime lastReviewedAt,
        boolean savedInNotebook
) {}
