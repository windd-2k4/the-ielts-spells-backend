package com.theieltsspells.aimentor.application.dto;

import java.util.List;

public record AiMentorChatResponse(
        String enReply,
        String viReply,
        List<String> suggestedChips,
        List<VocabTip> extractedVocab,
        MicroQuest microQuest,
        ActionCard actionCard,
        String provider
) {
    public record VocabTip(String word, String pos, String meaningVi, String cefr) {}

    public record MicroQuest(
            String question,
            List<String> options,
            int correctIndex,
            String explanationVi
    ) {}

    public record ActionCard(
            String actionType,
            String title,
            String subtitle,
            String targetCefr
    ) {}
}
