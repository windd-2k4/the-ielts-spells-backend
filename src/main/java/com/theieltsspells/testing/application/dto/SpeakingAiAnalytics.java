package com.theieltsspells.testing.application.dto;

import java.util.List;
import java.util.Map;

/** Canonical persisted and API contract for advanced Speaking analytics. */
public final class SpeakingAiAnalytics {
    private SpeakingAiAnalytics() { }

    public record PronunciationIssue(String word, String ipa, String issue) { }

    public record SpeechMetrics(
            Integer wordsPerMinute,
            Integer pauseCount,
            boolean pauseCountEstimated,
            Map<String, Integer> fillerWords,
            List<PronunciationIssue> pronunciationIssues
    ) {
        public static SpeechMetrics empty() {
            return new SpeechMetrics(null, null, false, Map.of(), List.of());
        }
    }

    public record AnswerUpgrade(
            String questionKey,
            String originalText,
            String upgradedText,
            List<String> collocations,
            String explanation
    ) { }
}
