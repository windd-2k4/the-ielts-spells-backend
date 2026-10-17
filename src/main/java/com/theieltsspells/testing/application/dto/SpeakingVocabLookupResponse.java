package com.theieltsspells.testing.application.dto;

import java.util.List;

public record SpeakingVocabLookupResponse(
        String query,
        String direction,
        String mainTranslation,
        String phonetic,
        String partOfSpeech,
        List<String> synonyms,
        List<ExampleItem> examples
) {
    public record ExampleItem(
            String english,
            String vietnamese
    ) {}
}
