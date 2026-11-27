package com.theieltsspells.testing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.testing.application.dto.SpeakingExaminerTurnResponse;
import com.theieltsspells.testing.domain.SpeakingExamState;
import com.theieltsspells.testing.domain.SpeakingSessionVariant;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Selects and snapshots a question-bank route; it deliberately never calls an LLM. */
@Component
@RequiredArgsConstructor
class SpeakingExaminerQuestionPlanner {
    static final String PART_TWO_LONG_TURN_SPEECH =
            "Your one minute of preparation time is up. Please start speaking now. Remember you have one to two minutes.";
    private final ObjectMapper objectMapper;

    Plan plan(String builderContent, SpeakingSessionVariant variant) {
        try {
            JsonNode parts = objectMapper.readTree(builderContent).path("parts");
            if (!parts.isArray()) throw invalid(variant);
            JsonNode partOne = part(parts, 1);
            JsonNode partTwo = part(parts, 2);
            JsonNode partThree = part(parts, 3);

            List<PlannedTurn> turns = new ArrayList<>();
            turns.add(checkpoint(SpeakingExamState.MIC_CHECK, "Microphone check. Please say a short sentence."));
            if (variant == SpeakingSessionVariant.FULL_MOCK) {
                if (partOne == null || partTwo == null || partThree == null) throw invalid(variant);
                turns.add(checkpoint(SpeakingExamState.IDENTITY_CHECK,
                        "Please state your full name for the identity check."));
                addPartOne(turns, partOne, 3, 3, variant, null);
                String sharedTopic = addPartTwo(turns, partTwo, variant);
                addPartThree(turns, partThree, sharedTopic, 1, Integer.MAX_VALUE, variant);
            } else if (variant == SpeakingSessionVariant.PART_1) {
                if (partOne == null) throw invalid(variant);
                addPartOne(turns, partOne, 3, 3, variant,
                        "Good morning. I will be your examiner today. Let's begin with Part 1.");
            } else if (variant == SpeakingSessionVariant.PART_2) {
                if (partTwo == null) throw invalid(variant);
                addPartTwo(turns, partTwo, variant);
            } else {
                if (partThree == null) throw invalid(variant);
                addPartThree(turns, partThree, valueOr(partThree, "topicKey", valueOr(partThree, "id", "part-3-topic")),
                        3, 5, variant);
            }
            turns.add(new PlannedTurn(SpeakingExamState.CLOSING, null, SpeakingExaminerTurnResponse.Kind.CLOSING,
                    null, "This is the end of the Speaking test. Thank you.", null, 15));
            return new Plan(List.copyOf(turns));
        } catch (BusinessRuleException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid(variant);
        }
    }

    private void addPartOne(List<PlannedTurn> turns, JsonNode partOne, int minimum, int maximum,
                            SpeakingSessionVariant variant, String firstSpeechPrefix) {
        int answerSeconds = positive(partOne.path("answerTimeSeconds").asInt(), 30);
        int selected = 0;
        for (JsonNode question : partOne.path("questions")) {
            String prompt = plain(question.path("promptText").asText());
            if (prompt.isBlank()) continue;
            String topicKey = question.path("topicKey").asText().trim();
            if (topicKey.isBlank()) topicKey = valueOr(partOne, "id", "part-1") + "-topic-" + (selected + 1);
            String speechText = selected == 0 && firstSpeechPrefix != null
                    ? firstSpeechPrefix + " " + prompt : prompt;
            turns.add(new PlannedTurn(SpeakingExamState.PART_1, 1, SpeakingExaminerTurnResponse.Kind.QUESTION,
                    topicKey, prompt, speechText, null,
                    positive(question.path("answerTimeSeconds").asInt(), answerSeconds)));
            if (++selected == maximum) break;
        }
        if (selected < minimum) throw invalid(variant);
    }

    private String addPartTwo(List<PlannedTurn> turns, JsonNode partTwo, SpeakingSessionVariant variant) {
        String topic = valueOr(partTwo, "topicKey", valueOr(partTwo, "id", "part-2-topic"));
        String cuePrompt = plain(partTwo.path("cueCardPromptHtml").asText());
        if (cuePrompt.isBlank()) cuePrompt = plain(partTwo.path("topicTitle").asText());
        if (cuePrompt.isBlank()) throw invalid(variant);
        List<String> bullets = new ArrayList<>();
        partTwo.path("cueCardBullets").forEach(value -> {
            String bullet = plain(value.asText());
            if (!bullet.isBlank()) bullets.add(bullet);
        });
        if (!bullets.isEmpty()) cuePrompt += " You should say: " + String.join("; ", bullets) + ".";
        turns.add(new PlannedTurn(SpeakingExamState.PART_2_LONG_TURN, 2, SpeakingExaminerTurnResponse.Kind.CUE_CARD,
                topic, cuePrompt, PART_TWO_LONG_TURN_SPEECH,
                positive(partTwo.path("preparationTimeSeconds").asInt(), 60),
                positive(partTwo.path("answerTimeSeconds").asInt(), 120)));
        String followUpPrompt = firstPrompt(partTwo.path("followUpQuestions"));
        if (followUpPrompt.isBlank()) throw invalid(variant);
        turns.add(new PlannedTurn(SpeakingExamState.PART_2_FOLLOW_UP, 2, SpeakingExaminerTurnResponse.Kind.FOLLOW_UP,
                topic, followUpPrompt, null, 30));
        return topic;
    }

    private void addPartThree(List<PlannedTurn> turns, JsonNode partThree, String topic, int minimum, int maximum,
                              SpeakingSessionVariant variant) {
        int answerSeconds = positive(partThree.path("answerTimeSeconds").asInt(), 45);
        int selected = 0;
        for (JsonNode question : partThree.path("questions")) {
            String prompt = plain(question.path("promptText").asText());
            if (prompt.isBlank()) continue;
            turns.add(new PlannedTurn(SpeakingExamState.PART_3, 3, SpeakingExaminerTurnResponse.Kind.QUESTION,
                    topic, prompt, null, positive(question.path("answerTimeSeconds").asInt(), answerSeconds)));
            if (++selected == maximum) break;
        }
        if (selected < minimum) throw invalid(variant);
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

    private BusinessRuleException invalid(SpeakingSessionVariant variant) {
        return switch (variant) {
            case FULL_MOCK -> new BusinessRuleException(
                    "Đề Speaking cần có ít nhất 3 câu Part 1, 1 cue card và follow-up Part 2, cùng câu hỏi Part 3");
            case PART_1 -> new BusinessRuleException("Đề Speaking không có đủ 3 câu hỏi Part 1 để luyện tập");
            case PART_2 -> new BusinessRuleException("Đề Speaking không có cue card và follow-up Part 2 hợp lệ");
            case PART_3 -> new BusinessRuleException("Đề Speaking không có đủ 3 câu hỏi Part 3 để luyện tập");
        };
    }

    record Plan(List<PlannedTurn> turns) {}

    record PlannedTurn(SpeakingExamState state, Integer partNo, SpeakingExaminerTurnResponse.Kind kind,
                       String topicKey, String promptText, String speechText,
                       Integer preparationTimeSeconds, Integer answerTimeSeconds) {
        PlannedTurn(SpeakingExamState state, Integer partNo, SpeakingExaminerTurnResponse.Kind kind,
                    String topicKey, String promptText, Integer preparationTimeSeconds, Integer answerTimeSeconds) {
            this(state, partNo, kind, topicKey, promptText, promptText, preparationTimeSeconds, answerTimeSeconds);
        }
    }
}
