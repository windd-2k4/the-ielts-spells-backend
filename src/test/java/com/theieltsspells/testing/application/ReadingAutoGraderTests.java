package com.theieltsspells.testing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingAutoGraderTests {

    private final ReadingAutoGrader grader = new ReadingAutoGrader();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsTextAnswerRegardlessOfCaseWhitespaceAndPunctuation() throws Exception {
        var question = question("SHORT_ANSWER", List.of("New York"), List.of("NYC"));

        var result = grader.evaluate(question, objectMapper.readTree("{\"value\":\"  new,   york! \"}"));

        assertThat(result.correct()).isTrue();
        assertThat(result.score()).isEqualByComparingTo("1");
        assertThat(result.normalizedAnswer()).containsExactly("new york");
    }

    @Test
    void acceptsConfiguredAlternativeAnswer() throws Exception {
        var question = question("SHORT_ANSWER", List.of("United Kingdom"), List.of("UK"));

        var result = grader.evaluate(question, objectMapper.readTree("{\"value\":\"uk\"}"));

        assertThat(result.correct()).isTrue();
    }

    @Test
    void requiresExactSetForMultipleAnswers() throws Exception {
        var question = question("MULTIPLE_ANSWERS", List.of("option-a", "option-c"), List.of());

        var correct = grader.evaluate(question, objectMapper.readTree("{\"values\":[\"option-c\",\"option-a\"]}"));
        var incomplete = grader.evaluate(question, objectMapper.readTree("{\"values\":[\"option-a\"]}"));

        assertThat(correct.correct()).isTrue();
        assertThat(incomplete.correct()).isFalse();
        assertThat(incomplete.score()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private ReadingAutoGrader.QuestionDefinition question(String type, List<String> answers, List<String> alternatives) {
        return new ReadingAutoGrader.QuestionDefinition(
                "question-1", 1, type, answers, alternatives, "Explanation", BigDecimal.ONE
        );
    }
}
