package com.theieltsspells.dictionary.application.dto;

import java.util.List;

public record DictionaryLookupResponse(
        String headword,
        String direction,
        String ipa,
        String phoneticUk,
        String phoneticUs,
        String audioUrl,
        String cefrLevel,
        String partOfSpeech,
        String summary,
        List<MeaningGroup> meanings,
        List<IeltsDefinition> ieltsDefinitions,
        List<WordFamilyItem> wordFamily,
        String etymology,
        String source,
        boolean savedInNotebook
) {
    public record MeaningGroup(String partOfSpeech, List<MeaningItem> items) {}

    public record MeaningItem(String meaning, List<ExampleItem> examples) {}

    public record ExampleItem(String text, String translation) {}

    public record IeltsDefinition(
            String partOfSpeech,
            String definitionEn,
            String definitionVi,
            String exampleEn,
            String exampleVi,
            List<String> synonyms,
            List<String> antonyms
    ) {}

    public record WordFamilyItem(String word, String pos, String meaningVi) {}
}
