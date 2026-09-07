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

    private TestBankResponse test(SkillType skill, String testType, Map<String, Object> content) {
        return new TestBankResponse(
                UUID.randomUUID(), "TST-0001", "Valid title", null, skill, testType,
                1, 1, 20, "v1.0-draft", "DRAFT", List.of(), 0,
                "Teacher", OffsetDateTime.now(), OffsetDateTime.now(), content, 1, null
        );
    }
}
