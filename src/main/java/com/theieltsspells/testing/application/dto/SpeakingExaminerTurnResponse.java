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
        Integer answerTimeSeconds
) {
    public enum Kind {
        CHECKPOINT,
        QUESTION,
        CUE_CARD,
        FOLLOW_UP,
        CLOSING
    }
}
