package com.theieltsspells.testing.application.crawl;

import java.util.Map;

public record CrawledTestDocument(
        String sourceTestId,
        String title,
        String sourceUrl,
        String extractedText,
        String html,
        int questionCount,
        String contentHash,
        Map<String, Object> metadata
) {
}
