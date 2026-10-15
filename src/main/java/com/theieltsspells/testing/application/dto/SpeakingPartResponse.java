package com.theieltsspells.testing.application.dto;

import java.util.List;

public record SpeakingPartResponse(
        String id,
        int partNo,
        String topicTitle,
        String cueCardPromptHtml,
        List<String> cueCardBullets,
        String audioUrl,
        boolean hintsEnabled,
        List<SpeakingHintStepResponse> hintSteps,
        int preparationTimeSeconds,
        int answerTimeSeconds,
        List<String> followUpQuestions,
        List<SpeakingQuestionResponse> questions,
        String sampleResponseText
) {}
