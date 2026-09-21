package com.theieltsspells.testing.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.util.HtmlSanitizer;
import com.theieltsspells.testing.application.dto.ReadingEvidenceMode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Converts a validated Reading builder snapshot into immutable, queryable
 * delivery rows. Student delivery reads these rows instead of an editable draft.
 */
@Service
@RequiredArgsConstructor
class ReadingVersionMaterializer {

    private static final Set<String> SOLUTION_VISIBILITIES = Set.of(
            "TEACHER_ONLY", "STUDENT_AFTER_ASSIGN", "STUDENT_AFTER_SUBMIT"
    );

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
        String rawContent = text(passage.get("content"), "");
        String sanitizedContent = HtmlSanitizer.sanitize(rawContent);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into public.test_version_sections(
                  id, test_version_id, section_key, section_no, title, content_html, display_order
                ) values (?, ?, ?, ?, ?, ?, ?)
                """, id, testVersionId, sectionKey, sectionNo,
                text(passage.get("title"), "Reading Passage " + sectionNo),
                sanitizedContent, displayOrder);
        return id;
    }

    private void materializeGroup(UUID testVersionId, UUID sectionId, Map<String, Object> group, int displayOrder) {
        String groupKey = required(group, "id", "Question Group");
        String typeFormat = required(group, "typeFormat", "Question Group");
        String title = required(group, "title", "Question Group");
        String instructions = required(group, "instructions", "Question Group");
        String sanitizedInstructions = HtmlSanitizer.sanitize(instructions);
        UUID groupId = UUID.randomUUID();
        jdbc.update("""
                insert into public.test_version_question_groups(
                  id, test_version_id, section_id, group_key, title, type_format, instructions,
                  answer_config, display_order
                ) values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
                """, groupId, testVersionId, sectionId, groupKey, title, typeFormat, sanitizedInstructions,
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
        String sanitizedPrompt = HtmlSanitizer.sanitize(prompt);
        var correctAnswers = strings(question.get("correctAnswers"));
        if (correctAnswers.isEmpty()) {
            throw new BusinessRuleException("Câu hỏi '" + questionKey + "' chưa có đáp án đúng");
        }
        var solution = solution(question);
        UUID questionId = UUID.randomUUID();
        jdbc.update("""
                insert into public.test_version_questions(
                  id, test_version_id, group_id, question_key, question_no, type_format, prompt,
                  correct_answers, acceptable_answers, explanation, reasoning_steps,
                  trap_analysis, vocabulary_notes, related_lesson_url, solution_visibility,
                  max_score, display_order
                ) values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?)
                """, questionId, testVersionId, groupId, questionKey,
                number(question.get("number"), displayOrder + 1),
                text(question.get("typeFormat"), groupType), sanitizedPrompt,
                json(correctAnswers), json(strings(question.get("acceptableAnswers"))),
                solution.explanation(), json(solution.reasoningSteps()), solution.trapAnalysis(),
                solution.vocabularyNotes(), solution.relatedLessonUrl(), solution.visibility(),
                decimal(question.get("maxScore")), displayOrder);

        insertOptions(testVersionId, groupId, questionId, maps(question.get("options")));
        insertEvidenceSpans(testVersionId, questionId, questionKey, question, solution);
    }

    /**
     * The builder has historically kept solution fields directly on a question.
     * A nested {@code solution} object is also accepted so that future clients
     * can use the same shape returned to students without breaking old drafts.
     */
    private SolutionDraft solution(Map<String, Object> question) {
        var nested = map(question.get("solution"));
        return new SolutionDraft(
                sanitizedHtml(value(nested, question, "explanation")),
                sanitizedHtmlStrings(value(nested, question, "reasoningSteps")),
                sanitizedHtml(value(nested, question, "trapAnalysis")),
                sanitizedHtml(value(nested, question, "vocabularyNotes")),
                relatedLessonUrl(value(nested, question, "relatedLessonUrl")),
                solutionVisibility(value(nested, question, "solutionVisibility"))
        );
    }

    private Object value(Map<String, Object> nested, Map<String, Object> question, String key) {
        return nested.containsKey(key) ? nested.get(key) : question.get(key);
    }

    private void insertEvidenceSpans(UUID testVersionId, UUID questionId, String questionKey,
                                     Map<String, Object> question, SolutionDraft solution) {
        var evidence = evidenceSpans(question, questionKey, solution);
        var seenIds = new HashSet<UUID>();
        for (int index = 0; index < evidence.size(); index++) {
            var item = evidence.get(index);
            UUID evidenceId = item.id();
            while (!seenIds.add(evidenceId)) {
                evidenceId = UUID.randomUUID();
            }
            jdbc.update("""
                    insert into public.test_version_question_evidence(
                      id, test_version_id, question_id, evidence_order, start_offset, end_offset,
                      quote, prefix_text, suffix_text, paragraph_key, label, evidence_mode
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, evidenceId, testVersionId, questionId, index, item.start(), item.end(),
                    item.quote(), item.prefix(), item.suffix(), item.paragraphKey(), item.label(), item.mode().name());
        }
    }

    /**
     * {@code evidenceSpans} supersedes the former single {@code passageSpan}.
     * If the new property is present, even an empty list deliberately means the
     * teacher has no directly linked evidence for that question.
     */
    private List<EvidenceDraft> evidenceSpans(Map<String, Object> question, String questionKey,
                                              SolutionDraft solution) {
        if (question.containsKey("evidenceSpans")) {
            return maps(question.get("evidenceSpans")).stream()
                    .map(span -> evidenceSpan(span, questionKey, solution))
                    .toList();
        }
        var legacy = map(question.get("passageSpan"));
        return legacy.isEmpty() ? List.of() : List.of(evidenceSpan(legacy, questionKey, solution));
    }

    private EvidenceDraft evidenceSpan(Map<String, Object> source, String questionKey, SolutionDraft solution) {
        ReadingEvidenceMode mode = evidenceMode(source, questionKey);
        Integer start = nullableInteger(source.get("start"));
        Integer end = nullableInteger(source.get("end"));
        if (mode == ReadingEvidenceMode.NO_DIRECT_EVIDENCE) {
            if (solution.explanation() == null || solution.explanation().isBlank()) {
                throw new BusinessRuleException("Bằng chứng 'không có thông tin trực tiếp' của câu hỏi '"
                        + questionKey + "' cần có lời giải cho học viên");
            }
            start = null;
            end = null;
        } else if (start == null || end == null || start < 0 || end <= start) {
            throw new BusinessRuleException("Bằng chứng của câu hỏi '" + questionKey
                    + "' phải có vị trí bắt đầu và kết thúc hợp lệ");
        }
        return new EvidenceDraft(
                uuid(source.get("id")), start, end, nullableText(source.get("quote")),
                nullableText(source.get("prefix")), nullableText(source.get("suffix")),
                nullableText(source.get("paragraphKey")), nullableText(source.get("label")), mode
        );
    }

    private ReadingEvidenceMode evidenceMode(Map<String, Object> source, String questionKey) {
        String raw = nullableText(source.get("mode"));
        if (raw == null) {
            return ReadingEvidenceMode.DIRECT_QUOTE;
        }
        try {
            return ReadingEvidenceMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException("Loại bằng chứng của câu hỏi '" + questionKey + "' không hợp lệ");
        }
    }

    private String solutionVisibility(Object value) {
        String visibility = nullableText(value);
        if (visibility == null) {
            return "STUDENT_AFTER_SUBMIT";
        }
        String normalized = visibility.toUpperCase(Locale.ROOT);
        if (!SOLUTION_VISIBILITIES.contains(normalized)) {
            throw new BusinessRuleException("Quyền hiển thị lời giải không hợp lệ");
        }
        return normalized;
    }

    private String relatedLessonUrl(Object value) {
        String url = nullableText(value);
        if (url == null) {
            return null;
        }
        try {
            URI parsed = URI.create(url);
            String scheme = parsed.getScheme();
            if (scheme == null || parsed.getHost() == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Unsupported URL");
            }
            return url;
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException("Liên kết bài học liên quan phải là URL http(s) hợp lệ");
        }
    }

    private void insertOptions(UUID testVersionId, UUID groupId, UUID questionId, List<Map<String, Object>> options) {
        for (int index = 0; index < options.size(); index++) {
            var option = options.get(index);
            String optionKey = required(option, "id", "Lựa chọn");
            String content = required(option, "text", "Lựa chọn");
            String sanitizedContent = HtmlSanitizer.sanitize(content);
            jdbc.update("""
                    insert into public.test_version_question_options(
                      test_version_id, group_id, question_id, option_key, option_code, content, display_order
                    ) values (?, ?, ?, ?, ?, ?, ?)
                    """, testVersionId, groupId, questionId, optionKey,
                    nullableText(option.get("code")) == null ? nullableText(option.get("label")) : nullableText(option.get("code")),
                    sanitizedContent, index);
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

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
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

    private List<String> sanitizedHtmlStrings(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(this::sanitizedHtml)
                .filter(item -> item != null)
                .toList();
    }

    private String sanitizedHtml(Object value) {
        String raw = nullableText(value);
        if (raw == null) {
            return null;
        }
        return nullableText(HtmlSanitizer.sanitize(raw));
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

    private Integer nullableInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private UUID uuid(Object value) {
        String raw = nullableText(value);
        if (raw != null) {
            try {
                return UUID.fromString(raw);
            } catch (IllegalArgumentException ignored) {
                // Builder draft identifiers can be legacy/non-UUID values.
            }
        }
        return UUID.randomUUID();
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

    private record SolutionDraft(
            String explanation,
            List<String> reasoningSteps,
            String trapAnalysis,
            String vocabularyNotes,
            String relatedLessonUrl,
            String visibility
    ) {
    }

    private record EvidenceDraft(
            UUID id,
            Integer start,
            Integer end,
            String quote,
            String prefix,
            String suffix,
            String paragraphKey,
            String label,
            ReadingEvidenceMode mode
    ) {
    }
}
