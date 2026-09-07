package com.theieltsspells.testing.application;

import com.theieltsspells.shared.application.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadingDraftValidatorTests {

    @Test
    void acceptsMatchingGroupWithSharedOptionBank() {
        var content = content(group(
                "MATCHING_FEATURES",
                Map.of(
                        "sharedOptions", List.of(Map.of("id", "option-a", "code", "A", "text", "The United States")),
                        "allowOptionReused", true
                ),
                List.of(question(22, List.of("option-a")))
        ));

        assertDoesNotThrow(() -> ReadingDraftValidator.validate(content));
    }

    @Test
    void rejectsMatchingGroupWithoutUsableOptionBank() {
        var content = content(group(
                "MATCHING_HEADINGS",
                Map.of("sharedOptions", List.of(Map.of("id", "option-i", "code", "i", "text", ""))),
                List.of(question(1, List.of("option-i")))
        ));

        assertThrows(BusinessRuleException.class, () -> ReadingDraftValidator.validate(content));
    }

    @Test
    void rejectsMultipleAnswersWhenSelectedCountDoesNotMatchGroupRule() {
        var content = content(group(
                "MULTIPLE_ANSWERS",
                Map.of("requiredAnswerCount", 2),
                List.of(question(1, List.of("option-a")))
        ));

        assertThrows(BusinessRuleException.class, () -> ReadingDraftValidator.validate(content));
    }

    @Test
    void rejectsCompletionGroupWithoutWordLimit() {
        var content = content(group(
                "TABLE_COMPLETION",
                Map.of("answerSource", "PASSAGE", "wordLimitRule", ""),
                List.of(question(1, List.of("water")))
        ));

        assertThrows(BusinessRuleException.class, () -> ReadingDraftValidator.validate(content));
    }

    @Test
    void rejectsFullReadingWithFewerThanThreePassages() {
        var content = content(group(
                "MULTIPLE_CHOICE",
                Map.of(),
                List.of(question(1, List.of("A")))
        ));

        assertThrows(BusinessRuleException.class, () -> ReadingDraftValidator.validate(content, true));
    }

    @Test
    void acceptsSingleReadingWithOnePassage() {
        var content = content(group(
                "MULTIPLE_CHOICE",
                Map.of(),
                List.of(question(1, List.of("A")))
        ));

        assertDoesNotThrow(() -> ReadingDraftValidator.validate(content, false));
    }

    private Map<String, Object> content(Map<String, Object> group) {
        return Map.of("passages", List.of(Map.of("questionGroups", List.of(group))));
    }

    private Map<String, Object> group(String type, Map<String, Object> extra, List<Map<String, Object>> questions) {
        var group = new java.util.HashMap<String, Object>();
        group.put("title", "Questions 1–5");
        group.put("typeFormat", type);
        group.put("instructions", "Complete the task.");
        group.put("questions", questions);
        group.putAll(extra);
        return group;
    }

    private Map<String, Object> question(int number, List<String> answers) {
        return Map.of(
                "number", number,
                "prompt", "Question content",
                "correctAnswers", answers
        );
    }
}
