package com.theieltsspells.testing.application.dto;

public record SpeakingHintOptionResponse(
        String id,
        String label,
        String phrase,
        String fullSentence
) {}
