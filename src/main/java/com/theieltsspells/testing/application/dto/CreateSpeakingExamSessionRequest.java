package com.theieltsspells.testing.application.dto;

import com.theieltsspells.testing.domain.SpeakingSessionVariant;

public record CreateSpeakingExamSessionRequest(SpeakingSessionVariant variant) {
    public CreateSpeakingExamSessionRequest {
        if (variant == null) variant = SpeakingSessionVariant.FULL_MOCK;
    }

    public static CreateSpeakingExamSessionRequest defaults() {
        return new CreateSpeakingExamSessionRequest(SpeakingSessionVariant.FULL_MOCK);
    }
}
