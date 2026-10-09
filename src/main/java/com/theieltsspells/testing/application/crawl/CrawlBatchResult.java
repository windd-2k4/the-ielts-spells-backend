package com.theieltsspells.testing.application.crawl;

import java.util.List;

public record CrawlBatchResult(
        List<CrawledTestDocument> documents,
        int discoveredCount,
        int skippedKnownCount,
        int skippedIncompleteCount,
        List<String> warnings
) {
    public CrawlBatchResult {
        documents = documents == null ? List.of() : List.copyOf(documents);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
