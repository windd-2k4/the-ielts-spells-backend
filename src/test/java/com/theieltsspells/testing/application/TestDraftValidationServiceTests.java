package com.theieltsspells.testing.application;

import com.theieltsspells.shared.persistence.enums.SkillType;
import com.theieltsspells.testing.application.dto.TestBankResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDraftValidationServiceTests {
    private final TestDraftValidationService service = new TestDraftValidationService();

    @Test
    void acceptsCompleteListeningFullTest() {
        var part = Map.<String, Object>of(
                "audioUrl", "https://files.example.test/part.mp3",
                "questionGroups", List.of(Map.of(
                        "title", "Questions 1-1",
                        "instructions", "Choose the correct answer.",
                        "questions", List.of(Map.of(
                                "number", 1,
                                "prompt", "Where will the speakers meet?",
                                "correctAnswers", List.of("A")
                        ))
                ))
        );
        var content = Map.<String, Object>of("parts", List.of(part, part, part, part));

        var result = service.validate(test(SkillType.LISTENING, "FULL_TEST", content));

        assertTrue(result.publishable());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void preventsPublishingAnIncompleteSpeakingCueCard() {
        var content = Map.<String, Object>of("parts", List.of(Map.of(
                "partNo", 2,
                "topicTitle", "A memorable journey",
                "cueCardPromptHtml", ""
        )));

        var result = service.validate(test(SkillType.SPEAKING, "SINGLE_SKILL", content));

        assertFalse(result.publishable());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.id().equals("speaking-2-cue-card")));
    }

    @Test
    void acceptsACompleteWritingFullTest() {
        var content = Map.<String, Object>of("tasks", List.of(
                Map.of("taskNo", 1, "promptHtml", "<p>Summarise the chart.</p>", "minWords", 150,
                        "suggestedTimeMinutes", 20, "imageUrl", "/api/v1/admin/library/media/chart/content",
                        "imageAltText", "Biểu đồ đường thể hiện số liệu theo thời gian"),
                Map.of("taskNo", 2, "promptHtml", "<p>Discuss both views.</p>", "minWords", 250, "suggestedTimeMinutes", 40)
        ));

        var result = service.validate(test(SkillType.WRITING, "FULL_TEST", content));

        assertTrue(result.publishable());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void rejectsWritingTaskOneWithoutImageOrAccessibleDescription() {
        var missingImage = Map.<String, Object>of("tasks", List.of(
                Map.of("taskNo", 1, "promptHtml", "<p>Summarise the chart.</p>", "minWords", 150,
                        "suggestedTimeMinutes", 20)
        ));
        var missingDescription = Map.<String, Object>of("tasks", List.of(
                Map.of("taskNo", 1, "promptHtml", "<p>Summarise the chart.</p>", "minWords", 150,
                        "suggestedTimeMinutes", 20, "imageUrl", "/api/v1/admin/library/media/chart/content")
        ));

        var imageResult = service.validate(test(SkillType.WRITING, "SINGLE_SKILL", missingImage));
        var descriptionResult = service.validate(test(SkillType.WRITING, "SINGLE_SKILL", missingDescription));

        assertFalse(imageResult.publishable());
        assertTrue(imageResult.issues().stream().anyMatch(issue -> issue.id().equals("writing-1-image")));
        assertFalse(descriptionResult.publishable());
        assertTrue(descriptionResult.issues().stream().anyMatch(issue -> issue.id().equals("writing-1-image-alt")));
    }

    @Test
    void rejectsDuplicateWritingTasksAndNonIeltsWordMinimum() {
        var content = Map.<String, Object>of("tasks", List.of(
                Map.of("taskNo", 1, "promptHtml", "<p>First chart.</p>", "minWords", 100, "suggestedTimeMinutes", 20),
                Map.of("taskNo", 1, "promptHtml", "<p>Second chart.</p>", "minWords", 150, "suggestedTimeMinutes", 20)
        ));

        var result = service.validate(test(SkillType.WRITING, "FULL_TEST", content));

        assertFalse(result.publishable());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.id().contains("task-number")));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.id().contains("minimum-standard")));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.id().equals("writing-full-task-numbers")));
    }

    private TestBankResponse test(SkillType skill, String testType, Map<String, Object> content) {
        return new TestBankResponse(
                UUID.randomUUID(), "TST-0001", "Valid title", null, skill, testType,
                1, 1, 20, "v1.0-draft", "DRAFT", List.of(), 0,
                "Teacher", OffsetDateTime.now(), OffsetDateTime.now(), content, 1, null
        );
    }
}
