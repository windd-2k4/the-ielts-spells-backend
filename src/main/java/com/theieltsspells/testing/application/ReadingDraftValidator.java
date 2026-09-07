package com.theieltsspells.testing.application;

import com.theieltsspells.shared.application.BusinessRuleException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ReadingDraftValidator {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "MULTIPLE_CHOICE",
            "MULTIPLE_ANSWERS",
            "FILL_IN_BLANK",
            "SHORT_ANSWER",
            "TRUE_FALSE_NOT_GIVEN",
            "YES_NO_NOT_GIVEN",
            "MATCHING_HEADINGS",
            "MATCHING_INFORMATION",
            "MATCHING_FEATURES",
            "MATCHING_SENTENCE_ENDINGS",
            "SENTENCE_COMPLETION",
            "SUMMARY_COMPLETION",
            "NOTE_COMPLETION",
            "TABLE_COMPLETION",
            "FLOW_CHART_COMPLETION",
            "DIAGRAM_LABELING"
    );

    private static final Set<String> SHARED_OPTION_TYPES = Set.of(
            "MATCHING_HEADINGS",
            "MATCHING_INFORMATION",
            "MATCHING_FEATURES",
            "MATCHING_SENTENCE_ENDINGS"
    );

    private static final Set<String> WORD_LIMIT_TYPES = Set.of(
            "FILL_IN_BLANK",
            "SHORT_ANSWER",
            "SENTENCE_COMPLETION",
            "SUMMARY_COMPLETION",
            "NOTE_COMPLETION",
            "TABLE_COMPLETION",
            "FLOW_CHART_COMPLETION",
            "DIAGRAM_LABELING"
    );

    private ReadingDraftValidator() {
    }

    static void validate(Map<String, Object> content) {
        validate(content, false);
    }

    static void validate(Map<String, Object> content, boolean fullReadingTest) {
        var passages = maps(content.get("passages"));
        if (fullReadingTest && passages.size() != 3) {
            throw new BusinessRuleException("Full Reading phải có đúng 3 passages trước khi xuất bản");
        }

        var groups = questionGroups(content);
        if (groups.isEmpty()) {
            throw new BusinessRuleException("Đề Reading phải có ít nhất một Question Group trước khi xuất bản");
        }

        for (var group : groups) {
            var title = text(group.get("title"));
            if (title.isBlank()) {
                throw new BusinessRuleException("Question Group phải có tiêu đề");
            }

            var type = text(group.get("typeFormat"));
            if (!SUPPORTED_TYPES.contains(type)) {
                throw new BusinessRuleException("Question Group '" + title + "' có dạng bài không được hỗ trợ");
            }
            if (text(group.get("instructions")).isBlank()) {
                throw new BusinessRuleException("Question Group '" + title + "' chưa có hướng dẫn làm bài");
            }

            var questions = maps(group.get("questions"));
            if (questions.isEmpty()) {
                throw new BusinessRuleException("Question Group '" + title + "' phải có ít nhất một câu hỏi");
            }

            var answerSource = text(group.get("answerSource"));
            boolean optionBankRequired = SHARED_OPTION_TYPES.contains(type)
                    || ("SUMMARY_COMPLETION".equals(type) && "OPTION_BANK".equals(answerSource));
            if (optionBankRequired && !hasUsableOption(group.get("sharedOptions"))) {
                throw new BusinessRuleException("Question Group '" + title + "' chưa có Option bank hợp lệ");
            }

            boolean passageAnswer = !"OPTION_BANK".equals(answerSource);
            if (WORD_LIMIT_TYPES.contains(type) && passageAnswer && text(group.get("wordLimitRule")).isBlank()) {
                throw new BusinessRuleException("Question Group '" + title + "' chưa cấu hình giới hạn từ");
            }

            int requiredAnswerCount = intValue(group.get("requiredAnswerCount"), 2);
            if ("MULTIPLE_ANSWERS".equals(type) && (requiredAnswerCount < 2 || requiredAnswerCount > 6)) {
                throw new BusinessRuleException("Question Group '" + title + "' có số đáp án cần chọn không hợp lệ");
            }

            for (var question : questions) {
                int number = intValue(question.get("number"), 0);
                if (text(question.get("prompt")).isBlank()) {
                    throw new BusinessRuleException("Câu " + number + " chưa có nội dung câu hỏi");
                }
                var correctAnswers = list(question.get("correctAnswers"));
                if (correctAnswers.isEmpty()) {
                    throw new BusinessRuleException("Câu " + number + " chưa có đáp án đúng");
                }
                if ("MULTIPLE_ANSWERS".equals(type) && correctAnswers.size() != requiredAnswerCount) {
                    throw new BusinessRuleException("Câu " + number + " phải có đúng " + requiredAnswerCount + " đáp án đúng");
                }
            }
        }
    }

    private static List<Map<?, ?>> questionGroups(Map<String, Object> content) {
        var passages = maps(content.get("passages"));
        if (!passages.isEmpty()) {
            return passages.stream()
                    .flatMap(passage -> maps(passage.get("questionGroups")).stream())
                    .toList();
        }
        return maps(content.get("questionGroups"));
    }

    private static boolean hasUsableOption(Object value) {
        return maps(value).stream().anyMatch(option ->
                !text(option.get("code")).isBlank() && !text(option.get("text")).isBlank());
    }

    private static List<Map<?, ?>> maps(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        var result = new ArrayList<Map<?, ?>>();
        for (var item : values) {
            if (item instanceof Map<?, ?> map) result.add(map);
        }
        return result;
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> values ? values : List.of();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
