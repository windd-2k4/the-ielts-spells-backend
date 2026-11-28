package com.theieltsspells.dictionary.application.dto;

import java.util.List;

public record TranslateTextResponse(
        String sourceText,
        String translatedText,
        String sourceLang,
        String targetLang,
        String provider,
        String ieltsTip,
        List<KeyVocabHighlight> keyVocabulary
) {
    public record KeyVocabHighlight(String term, String meaning, String partOfSpeech) {}
}
