package com.theieltsspells.testing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.testing.application.dto.SpeakingExaminerTurnResponse;
import com.theieltsspells.testing.domain.SpeakingExamState;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Selects and snapshots a question-bank route; it deliberately never calls an LLM. */
@Component
@RequiredArgsConstructor
class SpeakingExaminerQuestionPlanner {
    private final ObjectMapper objectMapper;

    Plan plan(String builderContent) {
        try {
            JsonNode parts = objectMapper.readTree(builderContent).path("parts");
            if (!parts.isArray()) throw invalid();
            JsonNode partOne = part(parts, 1);
            JsonNode partTwo = part(parts, 2);
            JsonNode partThree = part(parts, 3);
            if (partOne == null || partTwo == null || partThree == null) throw invalid();

            List<PlannedTurn> turns = new ArrayList<>();
            turns.add(checkpoint(SpeakingExamState.MIC_CHECK, "Microphone check. Please say a short sentence."));
            turns.add(checkpoint(SpeakingExamState.IDENTITY_CHECK, "Please state your full name for the identity check."));

            int partOneSeconds = positive(partOne.path("answerTimeSeconds").asInt(), 30);
            int selectedTopics = 0;
            for (JsonNode question : partOne.path("questions")) {
                String prompt = plain(question.path("promptText").asText());
                if (prompt.isBlank()) continue;
                String topicKey = question.path("topicKey").asText().trim();
                if (topicKey.isBlank()) topicKey = valueOr(partOne, "id", "part-1") + "-topic-" + (selectedTopics + 1);
                turns.add(new PlannedTurn(SpeakingExamState.PART_1, 1, SpeakingExaminerTurnResponse.Kind.QUESTION,
                        topicKey, prompt, null, positive(question.path("answerTimeSeconds").asInt(), partOneSeconds)));
                if (++selectedTopics == 3) break;
            }
            if (selectedTopics != 3) throw invalid();

            String sharedTopic = valueOr(partTwo, "topicKey", valueOr(partTwo, "id", "part-2-topic"));
            String cuePrompt = plain(partTwo.path("cueCardPromptHtml").asText());
            if (cuePrompt.isBlank()) cuePrompt = plain(partTwo.path("topicTitle").asText());
            if (cuePrompt.isBlank()) throw invalid();
            List<String> bullets = new ArrayList<>();
            partTwo.path("cueCardBullets").forEach(value -> {
                String bullet = plain(value.asText());
                if (!bullet.isBlank()) bullets.add(bullet);
            });
            if (!bullets.isEmpty()) cuePrompt += " You should say: " + String.join("; ", bullets) + ".";
            turns.add(new PlannedTurn(SpeakingExamState.PART_2_LONG_TURN, 2, SpeakingExaminerTurnResponse.Kind.CUE_CARD,
                    sharedTopic, cuePrompt, positive(partTwo.path("preparationTimeSeconds").asInt(), 60),
                    positive(partTwo.path("answerTimeSeconds").asInt(), 120)));
            String followUpPrompt = firstPrompt(partTwo.path("followUpQuestions"));
            if (followUpPrompt.isBlank()) throw invalid();
            turns.add(new PlannedTurn(SpeakingExamState.PART_2_FOLLOW_UP, 2, SpeakingExaminerTurnResponse.Kind.FOLLOW_UP,
                    sharedTopic, followUpPrompt, null, 30));

            int partThreeSeconds = positive(partThree.path("answerTimeSeconds").asInt(), 45);
            int partThreeQuestions = 0;
            for (JsonNode question : partThree.path("questions")) {
                String prompt = plain(question.path("promptText").asText());
                if (prompt.isBlank()) continue;
                turns.add(new PlannedTurn(SpeakingExamState.PART_3, 3, SpeakingExaminerTurnResponse.Kind.QUESTION,
                        sharedTopic, prompt, null, positive(question.path("answerTimeSeconds").asInt(), partThreeSeconds)));
                partThreeQuestions++;
            }
            if (partThreeQuestions == 0) throw invalid();
            turns.add(new PlannedTurn(SpeakingExamState.CLOSING, null, SpeakingExaminerTurnResponse.Kind.CLOSING,
                    null, "This is the end of the Speaking test. Please confirm that you are ready to finish.", null, 15));
            return new Plan(List.copyOf(turns));
        } catch (BusinessRuleException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private PlannedTurn checkpoint(SpeakingExamState state, String prompt) {
        return new PlannedTurn(state, null, SpeakingExaminerTurnResponse.Kind.CHECKPOINT, null, prompt, null, 15);
    }

    private JsonNode part(JsonNode parts, int partNo) {
        for (JsonNode value : parts) if (value.path("partNo").asInt() == partNo) return value;
        return null;
    }

    private String firstPrompt(JsonNode questions) {
        if (questions.isArray()) for (JsonNode question : questions) {
            String prompt = plain(question.path("promptText").asText(question.asText()));
            if (!prompt.isBlank()) return prompt;
        }
        return "";
    }

    private String valueOr(JsonNode value, String field, String fallback) {
        String text = value.path(field).asText().trim();
        return text.isBlank() ? fallback : text;
    }

    private String plain(String value) {
        return Jsoup.parse(value == null ? "" : value).text().trim();
    }

    private int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private BusinessRuleException invalid() {
        return new BusinessRuleException("Đề Speaking cần có ít nhất 3 topic Part 1, 1 cue card Part 2 và câu hỏi Part 3");
    }

    record Plan(List<PlannedTurn> turns) {}

    record PlannedTurn(SpeakingExamState state, Integer partNo, SpeakingExaminerTurnResponse.Kind kind,
                       String topicKey, String promptText, Integer preparationTimeSeconds, Integer answerTimeSeconds) {}
}
