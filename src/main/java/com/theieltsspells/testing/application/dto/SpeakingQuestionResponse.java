package com.theieltsspells.testing.application.dto;

import java.util.List;

public record SpeakingQuestionResponse(
        String id,
        String questionKey,
        int questionNo,
        String promptText,
        String audioUrl,
        boolean hintsEnabled,
        List<SpeakingHintStepResponse> hintSteps,
        String sampleResponseText,
        Integer answerTimeSeconds
) {}
