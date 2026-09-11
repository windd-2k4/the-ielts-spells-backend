package com.theieltsspells.testing.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Server-side scorer for the normalized Reading version model. */
final class ReadingAutoGrader {

    Evaluation evaluate(QuestionDefinition question, JsonNode answer) {
        var actual = normalizedValues(answer);
        if (actual.isEmpty()) {
            return new Evaluation(false, BigDecimal.ZERO, actual);
        }

        var expected = normalize(question.correctAnswers());
        var accepted = normalize(question.acceptableAnswers());
        boolean correct;
        if (isMultiple(question)) {
            correct = new LinkedHashSet<>(actual).equals(new LinkedHashSet<>(expected));
        } else {
            correct = actual.size() == 1 && (expected.contains(actual.getFirst()) || accepted.contains(actual.getFirst()));
        }
        return new Evaluation(correct, correct ? question.maxScore() : BigDecimal.ZERO, actual);
    }

    List<String> normalizedValues(JsonNode answer) {
        if (answer == null || answer.isNull()) {
            return List.of();
        }
        var values = new ArrayList<String>();
        if (answer.isObject()) {
            if (answer.has("values")) {
                collect(answer.get("values"), values);
            } else if (answer.has("value")) {
                collect(answer.get("value"), values);
            }
        } else {
            collect(answer, values);
        }
        return values.stream().map(this::normalize).filter(value -> !value.isBlank()).toList();
    }

    private boolean isMultiple(QuestionDefinition question) {
        return "MULTIPLE_ANSWERS".equals(question.typeFormat()) || question.correctAnswers().size() > 1;
    }

    private List<String> normalize(List<String> values) {
        return values.stream().map(this::normalize).filter(value -> !value.isBlank()).toList();
    }

    private void collect(JsonNode value, List<String> target) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isArray()) {
            value.forEach(item -> collect(item, target));
        } else if (value.isValueNode()) {
            target.add(value.asText());
        }
    }

    private String normalize(String value) {
        String collapsed = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        String decomposed = Normalizer.normalize(collapsed, Normalizer.Form.NFD);
        String withoutPunctuation = decomposed.replaceAll("[\\p{Punct}]", "");
        return withoutPunctuation.toLowerCase(Locale.ROOT).trim();
    }

    record QuestionDefinition(
            String questionKey,
            int questionNo,
            String typeFormat,
            List<String> correctAnswers,
            List<String> acceptableAnswers,
            String explanation,
            BigDecimal maxScore
    ) {
    }

    record Evaluation(boolean correct, BigDecimal score, List<String> normalizedAnswer) {
    }
}
