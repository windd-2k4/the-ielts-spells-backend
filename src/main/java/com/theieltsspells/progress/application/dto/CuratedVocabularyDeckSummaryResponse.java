package com.theieltsspells.progress.application.dto;

import java.util.UUID;

public record CuratedVocabularyDeckSummaryResponse(
        UUID id,
        String slug,
        String name,
        String category,
        String description,
        int totalWords,
        int notLearnedCount,
        int familiarCount,
        int masteredCount,
        int orderIndex
) {}
