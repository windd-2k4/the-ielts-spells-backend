package com.theieltsspells.testing.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converts a validated Reading builder snapshot into immutable, queryable
 * delivery rows. Student delivery reads these rows instead of an editable draft.
 */
@Service
@RequiredArgsConstructor
class ReadingVersionMaterializer {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional
    void materialize(UUID testVersionId, Map<String, Object> content) {
        jdbc.update("delete from public.test_version_sections where test_version_id = ?", testVersionId);

        var passages = passages(content);
        if (passages.isEmpty()) {
            throw new BusinessRuleException("Phiên bản Reading chưa có passage để xuất bản");
        }

        for (int passageIndex = 0; passageIndex < passages.size(); passageIndex++) {
            var passage = passages.get(passageIndex);
            var sectionId = insertSection(testVersionId, passage, passageIndex);
            var groups = maps(passage.get("questionGroups"));
            if (groups.isEmpty()) {
                throw new BusinessRuleException("Reading passage " + (passageIndex + 1) + " chưa có Question Group");
            }
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                materializeGroup(testVersionId, sectionId, groups.get(groupIndex), groupIndex);
            }
        }
    }

    @Transactional
    void ensureMaterialized(UUID testVersionId, Map<String, Object> content) {
        jdbc.queryForObject("select id from public.test_versions where id = ? for update", UUID.class, testVersionId);
        Integer count = jdbc.queryForObject(
                "select count(*) from public.test_version_sections where test_version_id = ?",
                Integer.class, testVersionId);
        if (count == null || count == 0) {
            materialize(testVersionId, content);
        }
    }

    private UUID insertSection(UUID testVersionId, Map<String, Object> passage, int displayOrder) {
        String sectionKey = required(passage, "id", "Passage");
        int sectionNo = number(passage.get("passageNo"), displayOrder + 1);
        return jdbc.queryForObject("""
                insert into public.test_version_sections(
                  test_version_id, section_key, section_no, title, content_html, display_order
                ) values (?, ?, ?, ?, ?, ?)
                returning id
                """, UUID.class, testVersionId, sectionKey, sectionNo,
                text(passage.get("title"), "Reading Passage " + sectionNo),
                text(passage.get("content"), ""), displayOrder);
    }

    private void materializeGroup(UUID testVersionId, UUID sectionId, Map<String, Object> group, int displayOrder) {
        String groupKey = required(group, "id", "Question Group");
        String typeFormat = required(group, "typeFormat", "Question Group");
        String title = required(group, "title", "Question Group");
        String instructions = required(group, "instructions", "Question Group");
        UUID groupId = jdbc.queryForObject("""
                insert into public.test_version_question_groups(
                  test_version_id, section_id, group_key, title, type_format, instructions,
                  answer_config, display_order
                ) values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
                returning id
                """, UUID.class, testVersionId, sectionId, groupKey, title, typeFormat, instructions,
                json(answerConfig(group)), displayOrder);

        insertOptions(testVersionId, groupId, null, maps(group.get("sharedOptions")));

        var questions = maps(group.get("questions"));
        if (questions.isEmpty()) {
            throw new BusinessRuleException("Question Group '" + title + "' chưa có câu hỏi");
        }
        for (int questionIndex = 0; questionIndex < questions.size(); questionIndex++) {
            materializeQuestion(testVersionId, groupId, typeFormat, questions.get(questionIndex), questionIndex);
        }
    }

    private void materializeQuestion(UUID testVersionId, UUID groupId, String groupType,
                                     Map<String, Object> question, int displayOrder) {
        String questionKey = required(question, "id", "Câu hỏi");
        String prompt = required(question, "prompt", "Câu hỏi");
        var correctAnswers = strings(question.get("correctAnswers"));
        if (correctAnswers.isEmpty()) {
            throw new BusinessRuleException("Câu hỏi '" + questionKey + "' chưa có đáp án đúng");
        }
        UUID questionId = jdbc.queryForObject("""
                insert into public.test_version_questions(
                  test_version_id, group_id, question_key, question_no, type_format, prompt,
                  correct_answers, acceptable_answers, explanation, max_score, display_order
                ) values (?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?)
                returning id
                """, UUID.class, testVersionId, groupId, questionKey,
                number(question.get("number"), displayOrder + 1),
                text(question.get("typeFormat"), groupType), prompt,
                json(correctAnswers), json(strings(question.get("acceptableAnswers"))),
                nullableText(question.get("explanation")), decimal(question.get("maxScore")), displayOrder);

        insertOptions(testVersionId, groupId, questionId, maps(question.get("options")));
    }

    private void insertOptions(UUID testVersionId, UUID groupId, UUID questionId, List<Map<String, Object>> options) {
        for (int index = 0; index < options.size(); index++) {
            var option = options.get(index);
            String optionKey = required(option, "id", "Lựa chọn");
            String content = required(option, "text", "Lựa chọn");
            jdbc.update("""
                    insert into public.test_version_question_options(
                      test_version_id, group_id, question_id, option_key, option_code, content, display_order
                    ) values (?, ?, ?, ?, ?, ?, ?)
                    """, testVersionId, groupId, questionId, optionKey,
                    nullableText(option.get("code")) == null ? nullableText(option.get("label")) : nullableText(option.get("code")),
                    content, index);
        }
    }

    private List<Map<String, Object>> passages(Map<String, Object> content) {
        var passages = maps(content.get("passages"));
        if (!passages.isEmpty()) {
            return passages;
        }

        var rootGroups = maps(content.get("questionGroups"));
        if (rootGroups.isEmpty()) {
            return List.of();
        }
        var section = new LinkedHashMap<String, Object>();
        section.put("id", "legacy-passage-1");
        section.put("passageNo", 1);
        section.put("title", text(content.get("title"), "Reading Passage 1"));
        section.put("content", legacyPassageContent(content));
        section.put("questionGroups", rootGroups);
        return List.of(section);
    }

    private String legacyPassageContent(Map<String, Object> content) {
        Object value = content.get("passageContent");
        if (value instanceof Map<?, ?> values) {
            return text(values.get("1"), "");
        }
        return text(value, "");
    }

    private Map<String, Object> answerConfig(Map<String, Object> group) {
        var config = new LinkedHashMap<String, Object>();
        copy(group, config, "answerSource");
        copy(group, config, "wordLimitRule");
        copy(group, config, "requiredAnswerCount");
        copy(group, config, "allowOptionReused");
        copy(group, config, "gapFillTemplate");
        copy(group, config, "gapFillLayout");
        copy(group, config, "illustration");
        return config;
    }

    private void copy(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        var result = new ArrayList<Map<String, Object>>();
        for (Object item : values) {
            if (item instanceof Map<?, ?> map) {
                var normalized = new LinkedHashMap<String, Object>();
                map.forEach((key, itemValue) -> normalized.put(String.valueOf(key), itemValue));
                result.add(normalized);
            }
        }
        return result;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(item -> item != null && !item.toString().isBlank())
                .map(item -> item.toString().trim())
                .toList();
    }

    private String required(Map<String, Object> values, String key, String subject) {
        String value = nullableText(values.get(key));
        if (value == null) {
            throw new BusinessRuleException(subject + " thiếu " + key + " để xuất bản");
        }
        return value;
    }

    private String text(Object value, String fallback) {
        String text = nullableText(value);
        return text == null ? fallback : text;
    }

    private String nullableText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private int number(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return value == null ? BigDecimal.ONE : new BigDecimal(value.toString());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ONE;
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleException("Không thể chuẩn hóa nội dung Reading để xuất bản");
        }
    }
}
