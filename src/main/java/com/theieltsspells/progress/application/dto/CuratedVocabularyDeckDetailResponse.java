package com.theieltsspells.progress.application.dto;

import java.util.List;

public record CuratedVocabularyDeckDetailResponse(
        CuratedVocabularyDeckSummaryResponse deck,
        List<CuratedVocabularyItemResponse> items
) {}
