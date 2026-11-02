package com.theieltsspells.shared.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReadingAttemptGradedEvent(
        UUID attemptId,
        UUID learnerId,
        UUID testVersionId,
        String skill,
        String title,
        OffsetDateTime submittedAt,
        List<QuestionEvidence> evidence
) {
    public ReadingAttemptGradedEvent {
        evidence = List.copyOf(evidence);
    }

    public record QuestionEvidence(
            String questionKey,
            String questionType,
            boolean answered,
            Boolean correct
    ) {
    }
}

