package com.theieltsspells.testing.application;

import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.testing.application.dto.TestValidationIssueResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadingDraftValidatorTests {

    @Test
    void acceptsMatchingGroupWithSharedOptionBank() {
        var content = content(group(
                "MATCHING_FEATURES",
                Map.of(
                        "id", "group-1",
                        "sharedOptions", List.of(
                                Map.of("id", "option-a", "code", "A", "text", "The United States"),
                                Map.of("id", "option-b", "code", "B", "text", "Canada")
                        ),
                        "allowOptionReused", true
                ),
                List.of(question("q-1", 1, List.of("option-a")))
        ));

        assertDoesNotThrow(() -> ReadingDraftValidator.validate(content));
    }

    @Test
    void rejectsMatchingGroupWithoutUsableOptionBank() {
        var content = content(group(
                "MATCHING_HEADINGS",
                Map.of(
                        "id", "group-1",
                        "sharedOptions", List.of(Map.of("id", "option-i", "code", "i", "text", ""))
                ),
                List.of(question("q-1", 1, List.of("option-i")))
        ));

        assertThrows(BusinessRuleException.class, () -> ReadingDraftValidator.validate(content));
    }

    @Test
    void rejectsMultipleAnswersWhenSelectedCountDoesNotMatchGroupRule() {
        var content = content(group(
                "MULTIPLE_ANSWERS",
                Map.of(
                        "id", "group-1",
                        "requiredAnswerCount", 2
                ),
                List.of(question("q-1", 1, List.of("option-a")))
        ));

        assertThrows(BusinessRuleException.class, () -> ReadingDraftValidator.validate(content));
    }

    @Test
    void rejectsCompletionGroupWithoutWordLimit() {
        var content = content(group(
                "TABLE_COMPLETION",
                Map.of(
                        "id", "group-1",
                        "answerSource", "PASSAGE",
                        "wordLimitRule", ""
                ),
                List.of(question("q-1", 1, List.of("water")))
        ));

        assertThrows(BusinessRuleException.class, () -> ReadingDraftValidator.validate(content));
    }

    @Test
    void rejectsFullReadingWithFewerThanThreePassages() {
        var content = content(group(
                "MULTIPLE_CHOICE",
                Map.of("id", "group-1"),
                List.of(question("q-1", 1, List.of("q-1-opt-a")))
        ));

        assertThrows(BusinessRuleException.class, () -> ReadingDraftValidator.validate(content, true));
    }

    @Test
    void acceptsSingleReadingWithOnePassage() {
        var content = content(group(
                "MULTIPLE_CHOICE",
                Map.of(
                        "id", "group-1",
                        "answerSource", "PASSAGE"
                ),
                List.of(question("q-1", 1, List.of("q-1-opt-a")))
        ));

        assertDoesNotThrow(() -> ReadingDraftValidator.validate(content, false));
    }

    @Test
    void rejectsNonSequentialQuestionNumbering() {
        var content = content(group(
                "MULTIPLE_CHOICE",
                Map.of("id", "group-1"),
                List.of(question("q-1", 1, List.of("q-1-opt-a")), question("q-2", 3, List.of("q-2-opt-b")))
        ));

        var issues = new ArrayList<TestValidationIssueResponse>();
        ReadingDraftValidator.validate(testResponse(content, "SINGLE_SKILL", 20), issues);
        assertTrue(issues.stream().anyMatch(issue -> issue.id().contains("numbering")));
    }

    @Test
    void rejectsDuplicateIds() {
        Map<String, Object> content = Map.of(
                "passages", List.of(
                        Map.of(
                                "id", "duplicate-id",
                                "passageNo", 1,
                                "title", "Passage 1",
                                "content", "<p>Reading content</p>",
                                "questionGroups", List.of(group(
                                        "MULTIPLE_CHOICE",
                                        Map.of("id", "duplicate-id"),
                                        List.of(question("q-1", 1, List.of("q-1-opt-a")))
                                ))
                        )
                )
        );

        var issues = new ArrayList<TestValidationIssueResponse>();
        ReadingDraftValidator.validate(testResponse(content, "SINGLE_SKILL", 20), issues);
        assertTrue(issues.stream().anyMatch(issue -> issue.message().contains("bị trùng lặp")));
    }

    @Test
    void rejectsInvalidTfngAnswers() {
        var content = content(group(
                "TRUE_FALSE_NOT_GIVEN",
                Map.of("id", "group-1"),
                List.of(question("q-1", 1, List.of("INVALID_ANSWER")))
        ));

        assertThrows(BusinessRuleException.class, () -> ReadingDraftValidator.validate(content, false));
    }

    @Test
    void rejectsMultipleChoiceAnswerThatDoesNotReferenceAnOptionId() {
        var content = content(group(
                "MULTIPLE_CHOICE",
                Map.of("id", "group-1"),
                List.of(question("q-1", 1, List.of("A")))
        ));

        var issues = new ArrayList<TestValidationIssueResponse>();
        ReadingDraftValidator.validate(testResponse(content, "SINGLE_SKILL", 20), issues);
        assertTrue(issues.stream().anyMatch(issue -> issue.id().contains("answer-reference")));
    }

    @Test
    void rejectsDuplicateSharedAnswerWhenReuseIsDisabled() {
        var content = content(group(
                "MATCHING_HEADINGS",
                Map.of(
                        "id", "group-1",
                        "allowOptionReused", false,
                        "sharedOptions", List.of(
                                Map.of("id", "heading-i", "code", "i", "text", "First heading"),
                                Map.of("id", "heading-ii", "code", "ii", "text", "Second heading")
                        )
                ),
                List.of(
                        question("q-1", 1, List.of("heading-i")),
                        question("q-2", 2, List.of("heading-i"))
                )
        ));

        var issues = new ArrayList<TestValidationIssueResponse>();
        ReadingDraftValidator.validate(testResponse(content, "SINGLE_SKILL", 20), issues);
        assertTrue(issues.stream().anyMatch(issue -> issue.id().contains("option-reused")));
    }

    @Test
    void rejectsGapTemplateWithWrongOrDuplicatePlaceholders() {
        var content = content(group(
                "TABLE_COMPLETION",
                Map.of(
                        "id", "group-1",
                        "answerSource", "PASSAGE",
                        "wordLimitRule", "NO MORE THAN TWO WORDS",
                        "gapFillTemplate", "Row [[1]], duplicate [[1]]"
                ),
                List.of(
                        question("q-1", 1, List.of("water")),
                        question("q-2", 2, List.of("steam"))
                )
        ));

        var issues = new ArrayList<TestValidationIssueResponse>();
        ReadingDraftValidator.validate(testResponse(content, "SINGLE_SKILL", 20), issues);
        assertTrue(issues.stream().anyMatch(issue -> issue.id().contains("gap-template")));
    }

    @Test
    void reportsAllIndependentValidationErrors() {
        var content = content(group(
                "MULTIPLE_CHOICE",
                Map.of("id", "group-1"),
                List.of(Map.of(
                        "id", "q-1",
                        "number", 2,
                        "prompt", "",
                        "correctAnswers", List.of("missing-option"),
                        "maxScore", 0,
                        "options", List.of(
                                Map.of("id", "opt-a", "code", "A", "text", ""),
                                Map.of("id", "opt-b", "code", "A", "text", "Choice B")
                        )
                ))
        ));

        var issues = new ArrayList<TestValidationIssueResponse>();
        ReadingDraftValidator.validate(testResponse(content, "SINGLE_SKILL", 20), issues);
        assertTrue(issues.size() >= 5);
        assertTrue(issues.stream().anyMatch(issue -> issue.id().contains("numbering")));
        assertTrue(issues.stream().anyMatch(issue -> issue.id().contains("answer-reference")));
    }

    private Map<String, Object> content(Map<String, Object> group) {
        var map = new java.util.HashMap<String, Object>();
        map.put("passages", List.of(Map.of(
                "id", "passage-1",
                "passageNo", 1,
                "title", "Reading Passage 1",
                "content", "<p>Reading text content</p>",
                "questionGroups", List.of(group)
        )));
        return map;
    }

    private Map<String, Object> group(String type, Map<String, Object> extra, List<Map<String, Object>> questions) {
        var group = new java.util.HashMap<String, Object>();
        group.put("id", "group-1");
        group.put("title", "Questions 1–5");
        group.put("typeFormat", type);
        group.put("instructions", "Complete the task.");
        group.put("questions", questions);
        group.putAll(extra);
        return group;
    }

    private Map<String, Object> question(String id, int number, List<String> answers) {
        return Map.of(
                "id", id,
                "number", number,
                "prompt", "Question content",
                "correctAnswers", answers,
                "options", List.of(
                        Map.of("id", id + "-opt-a", "code", "A", "text", "Option A"),
                        Map.of("id", id + "-opt-b", "code", "B", "text", "Option B")
                )
        );
    }

    private com.theieltsspells.testing.application.dto.TestBankResponse testResponse(Map<String, Object> content, String testType, int duration) {
        return new com.theieltsspells.testing.application.dto.TestBankResponse(
                java.util.UUID.randomUUID(), "TEST-01", "Reading Test Title", "Description",
                com.theieltsspells.shared.persistence.enums.SkillType.READING, testType, 1, 1, duration, "v1.0",
                "DRAFT", List.of(), 0, "Admin", java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now(),
                content, 1, null
        );
    }
}
