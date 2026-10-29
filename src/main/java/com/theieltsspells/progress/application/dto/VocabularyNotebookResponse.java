package com.theieltsspells.progress.application.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record VocabularyNotebookResponse(
        Summary summary,
        List<Topic> topics,
        List<Card> cards
) {
    public record Summary(int total, int notLearned, int familiar, int mastered) {}

    public record Topic(UUID id, String name, int cardCount, int masteredCount) {}

    public record Card(
            UUID id,
            UUID topicId,
            String term,
            String meaning,
            String exampleSentence,
            String partOfSpeech,
            String note,
            String sourceLabel,
            String masteryStatus,
            OffsetDateTime createdAt,
            OffsetDateTime lastReviewedAt
    ) {}
}

