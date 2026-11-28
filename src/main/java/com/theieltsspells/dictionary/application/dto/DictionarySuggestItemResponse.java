package com.theieltsspells.dictionary.application.dto;

public record DictionarySuggestItemResponse(
        String headword,
        String direction,
        String ipa,
        String cefrLevel,
        String partOfSpeech,
        String summary
) {}
