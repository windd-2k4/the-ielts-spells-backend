package com.theieltsspells.testing.application.dto;

import java.util.UUID;

public record SpeakingExaminerTurnResponse(
        UUID turnId,
        int sequenceNo,
        Integer partNo,
        Kind kind,
        String topicKey,
        String promptText,
        String audioUrl,
        Integer preparationTimeSeconds,
        Integer answerTimeSeconds,
        boolean adaptiveFollowUp
) {
    public SpeakingExaminerTurnResponse(
            UUID turnId,
            int sequenceNo,
            Integer partNo,
            Kind kind,
            String topicKey,
            String promptText,
            String audioUrl,
            Integer preparationTimeSeconds,
            Integer answerTimeSeconds
    ) {
        this(turnId, sequenceNo, partNo, kind, topicKey, promptText, audioUrl,
                preparationTimeSeconds, answerTimeSeconds, false);
    }

    public enum Kind {
        CHECKPOINT,
        QUESTION,
        CUE_CARD,
        FOLLOW_UP,
        CLOSING
    }
}
