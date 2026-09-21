package com.theieltsspells.testing.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.persistence.enums.SkillType;
import com.theieltsspells.shared.web.PageResponse;
import com.theieltsspells.testing.application.dto.StudentPracticeCatalogItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentPracticeCatalogService {
    private static final Set<SkillType> IELTS_SKILLS = Set.of(
            SkillType.READING, SkillType.LISTENING, SkillType.WRITING, SkillType.SPEAKING
    );
    private static final Set<String> TEST_TYPES = Set.of("ALL", "FULL_TEST", "SINGLE_SKILL");
    private static final Set<String> PROGRESS = Set.of("ALL", "NOT_STARTED", "IN_PROGRESS", "COMPLETED");
    private static final Map<SkillType, List<String>> QUESTION_TYPES = Map.of(
            SkillType.READING, List.of(
                    "MATCHING_HEADINGS", "TRUE_FALSE_NOT_GIVEN", "YES_NO_NOT_GIVEN", "MULTIPLE_CHOICE",
                    "MULTIPLE_ANSWERS", "MATCHING_INFORMATION", "MATCHING_FEATURES",
                    "MATCHING_SENTENCE_ENDINGS", "FILL_IN_BLANK", "SHORT_ANSWER", "SENTENCE_COMPLETION",
                    "SUMMARY_COMPLETION", "NOTE_COMPLETION", "TABLE_COMPLETION", "FLOW_CHART_COMPLETION",
                    "DIAGRAM_LABELING"),
            SkillType.LISTENING, List.of(
                    "FILL_IN_BLANK", "MAP_DIAGRAM_LABEL", "MULTIPLE_CHOICE", "MULTIPLE_ANSWERS",
                    "MATCHING_INFORMATION", "SUMMARY_COMPLETION"),
            SkillType.WRITING, List.of(
                    "LINE_GRAPH", "BAR_CHART", "PIE_CHART", "TABLE", "MIXED_GRAPH", "MAP", "PROCESS", "ESSAY"),
            SkillType.SPEAKING, List.of("PERSONAL_QUESTIONS", "CUE_CARD", "DISCUSSION")
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PageResponse<StudentPracticeCatalogItemResponse> list(
            UUID studentId,
            SkillType skill,
            String query,
            String testType,
            String format,
            String questionTypes,
            String progress,
            int page,
            int size
    ) {
        if (skill == null || !IELTS_SKILLS.contains(skill)) {
            throw new BusinessRuleException("Vui lòng chọn một trong bốn kỹ năng IELTS");
        }
        String normalizedType = allowed(testType, TEST_TYPES, "ALL");
        String normalizedProgress = allowed(progress, PROGRESS, "ALL");
        List<String> normalizedQuestionTypes = questionTypes(skill, questionTypes);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 48);

        var where = new StringBuilder("""
                 where test.status = 'PUBLISHED'
                   and test.current_published_version_id = version.id
                   and version.primary_skill = cast(? as public.skill_type)
                """);
        var args = new ArrayList<Object>();
        args.add(skill.name());

        if (query != null && !query.isBlank()) {
            where.append(" and (lower(version.title) like ? or lower(test.code) like ? or lower(coalesce(version.description, '')) like ?)");
            String keyword = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
        }
        if (!"ALL".equals(normalizedType)) {
            where.append(" and version.test_type = ?");
            args.add(normalizedType);
        }
        if (format != null && !format.isBlank() && !"ALL".equalsIgnoreCase(format)) {
            String normalizedFormat = format.trim().toUpperCase(Locale.ROOT);
            where.append(" and (upper(version.builder_content ->> 'format') = ? or version.tags @> cast(? as jsonb))");
            args.add(normalizedFormat);
            args.add(json(List.of(normalizedFormat)));
        }
        if (!normalizedQuestionTypes.isEmpty()) {
            where.append(" and (");
            for (int index = 0; index < normalizedQuestionTypes.size(); index++) {
                if (index > 0) where.append(" or ");
                String questionType = normalizedQuestionTypes.get(index);
                where.append("(upper(version.title) like ? or upper(version.title) like ? or upper(test.code) like ? or upper(test.code) like ? or upper(version.builder_content::text) like ? or upper(version.builder_content::text) like ? or upper(version.tags::text) like ?");
                args.add("%" + questionType + "%");
                args.add("%" + questionType.replace('_', ' ') + "%");
                args.add("%" + questionType + "%");
                args.add("%" + questionType.replace('_', ' ') + "%");
                args.add("%" + questionType + "%");
                args.add("%" + questionType.replace('_', ' ') + "%");
                args.add("%" + questionType + "%");
                if (skill == SkillType.SPEAKING) {
                    int partNo = switch (questionType) {
                        case "PERSONAL_QUESTIONS" -> 1;
                        case "CUE_CARD" -> 2;
                        default -> 3;
                    };
                    where.append(" or version.builder_content @? '$.parts[*] ? (@.partNo == ").append(partNo).append(")'");
                } else if (skill == SkillType.WRITING && "ESSAY".equals(questionType)) {
                    where.append(" or version.builder_content @? '$.tasks[*] ? (@.taskNo == 2)'");
                }
                where.append(")");
            }
            where.append(")");
        }
        switch (normalizedProgress) {
            case "NOT_STARTED" -> {
                where.append(" and not exists (select 1 from public.test_attempts attempt where attempt.student_id = ? and attempt.test_version_id = version.id and attempt.attempt_origin = 'SELF_PRACTICE')");
                args.add(studentId);
            }
            case "IN_PROGRESS" -> {
                where.append(" and exists (select 1 from public.test_attempts attempt where attempt.student_id = ? and attempt.test_version_id = version.id and attempt.attempt_origin = 'SELF_PRACTICE' and attempt.status = 'IN_PROGRESS')");
                args.add(studentId);
            }
            case "COMPLETED" -> {
                where.append(" and exists (select 1 from public.test_attempts attempt where attempt.student_id = ? and attempt.test_version_id = version.id and attempt.attempt_origin = 'SELF_PRACTICE' and attempt.status <> 'IN_PROGRESS')");
                args.add(studentId);
            }
            default -> { }
        }

        String from = " from public.tests test join public.test_versions version on version.id = test.current_published_version_id ";
        Long total = jdbc.queryForObject("select count(*)" + from + where, Long.class, args.toArray());

        var dataArgs = new ArrayList<Object>();
        dataArgs.add(studentId);
        dataArgs.add(studentId);
        dataArgs.add(studentId);
        dataArgs.addAll(args);
        dataArgs.add(safeSize);
        dataArgs.add(safePage * safeSize);

        var items = jdbc.query("""
                select test.id test_id, version.id test_version_id, test.code, version.title,
                  version.description, version.primary_skill, version.test_type,
                  version.duration_minutes, version.tags::text tags,
                  version.builder_content::text builder_content, version.published_at,
                  (select count(*) from public.test_attempts attempt
                    where attempt.student_id = ? and attempt.test_version_id = version.id
                      and attempt.attempt_origin = 'SELF_PRACTICE') attempts_count,
                  active_attempt.id active_attempt_id,
                  active_attempt.expires_at active_attempt_expires_at,
                  previous_attempt.final_score last_score,
                  (select count(*) from public.test_version_questions question
                    where question.test_version_id = version.id) materialized_questions_count
                """ + from + """
                left join lateral (
                  select attempt.id, attempt.expires_at
                  from public.test_attempts attempt
                  where attempt.student_id = ? and attempt.test_version_id = version.id
                    and attempt.attempt_origin = 'SELF_PRACTICE' and attempt.status = 'IN_PROGRESS'
                  order by attempt.started_at desc limit 1
                ) active_attempt on true
                left join lateral (
                  select coalesce(attempt.final_score, attempt.auto_score) final_score
                  from public.test_attempts attempt
                  where attempt.student_id = ? and attempt.test_version_id = version.id
                    and attempt.attempt_origin = 'SELF_PRACTICE' and attempt.status <> 'IN_PROGRESS'
                  order by attempt.submitted_at desc nulls last, attempt.started_at desc limit 1
                ) previous_attempt on true
                """ + where + " order by version.published_at desc, version.title limit ? offset ?",
                this::mapRow, dataArgs.toArray());

        long count = total == null ? 0 : total;
        int totalPages = (int) Math.ceil((double) count / safeSize);
        return new PageResponse<>(items, safePage, safeSize, count, totalPages,
                safePage == 0, safePage + 1 >= totalPages);
    }

    private StudentPracticeCatalogItemResponse mapRow(ResultSet rs, int ignored) throws SQLException {
        SkillType skill = SkillType.valueOf(rs.getString("primary_skill"));
        Map<String, Object> content = readMap(rs.getString("builder_content"));
        return new StudentPracticeCatalogItemResponse(
                rs.getObject("test_id", UUID.class),
                rs.getObject("test_version_id", UUID.class),
                rs.getString("code"),
                rs.getString("title"),
                rs.getString("description"),
                skill,
                rs.getString("test_type"),
                text(content.get("format"), rs.getString("test_type")),
                sectionsCount(skill, content),
                itemCount(skill, content, rs.getInt("materialized_questions_count")),
                rs.getInt("duration_minutes"),
                readStrings(rs.getString("tags")),
                detectedQuestionTypes(skill, content, readStrings(rs.getString("tags")),
                        rs.getString("title"), rs.getString("code")),
                coverMap(content.get("coverImage")),
                rs.getObject("published_at", java.time.OffsetDateTime.class),
                rs.getInt("attempts_count"),
                rs.getObject("active_attempt_id", UUID.class),
                rs.getObject("active_attempt_expires_at", java.time.OffsetDateTime.class),
                rs.getBigDecimal("last_score"),
                skill == SkillType.READING && rs.getInt("materialized_questions_count") > 0
        );
    }

    private int sectionsCount(SkillType skill, Map<String, Object> content) {
        return switch (skill) {
            case READING -> maps(content.get("passages")).size();
            case LISTENING -> maps(content.get("parts")).size();
            case WRITING -> maps(content.containsKey("tasks") ? content.get("tasks") : content.get("writingTasks")).size();
            case SPEAKING -> maps(content.get("parts")).size();
            default -> 0;
        };
    }

    private int itemCount(SkillType skill, Map<String, Object> content, int materializedQuestions) {
        if (skill == SkillType.READING && materializedQuestions > 0) {
            return materializedQuestions;
        }
        if (skill == SkillType.WRITING) {
            return sectionsCount(skill, content);
        }
        int count = 0;
        for (var section : maps(skill == SkillType.READING ? content.get("passages") : content.get("parts"))) {
            if (skill == SkillType.SPEAKING) {
                count += maps(section.get("questions")).size();
                if (!text(section.get("cueCardPromptHtml"), "").isBlank()) count++;
            } else {
                for (var group : maps(section.get("questionGroups"))) {
                    count += maps(group.get("questions")).size();
                }
            }
        }
        return count;
    }

    private String allowed(String value, Set<String> allowed, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BusinessRuleException("Bộ lọc danh mục luyện đề không hợp lệ");
        }
        return normalized;
    }

    private List<String> questionTypes(SkillType skill, String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> allowed = QUESTION_TYPES.getOrDefault(skill, List.of());
        var result = new LinkedHashSet<String>();
        for (String item : value.split(",")) {
            String normalized = item.trim().toUpperCase(Locale.ROOT);
            if (!normalized.isBlank() && !allowed.contains(normalized)) {
                throw new BusinessRuleException("Dạng câu hỏi không phù hợp với kỹ năng đã chọn");
            }
            if (!normalized.isBlank()) result.add(normalized);
        }
        return List.copyOf(result);
    }

    private List<String> detectedQuestionTypes(
            SkillType skill,
            Map<String, Object> content,
            List<String> tags,
            String title,
            String code
    ) {
        List<String> allowed = QUESTION_TYPES.getOrDefault(skill, List.of());
        String haystack;
        try {
            haystack = (title + " " + code + " " + objectMapper.writeValueAsString(content) + " " + String.join(" ", tags))
                    .toUpperCase(Locale.ROOT);
        } catch (Exception exception) {
            haystack = (title + " " + code + " " + String.join(" ", tags)).toUpperCase(Locale.ROOT);
        }
        var result = new LinkedHashSet<String>();
        for (String type : allowed) {
            if (haystack.contains(type) || haystack.contains(type.replace('_', ' '))) result.add(type);
        }
        if (skill == SkillType.SPEAKING) {
            for (var part : maps(content.get("parts"))) {
                int partNo = number(part.get("partNo"));
                if (partNo == 1) result.add("PERSONAL_QUESTIONS");
                if (partNo == 2) result.add("CUE_CARD");
                if (partNo == 3) result.add("DISCUSSION");
            }
        }
        if (skill == SkillType.WRITING) {
            Object source = content.containsKey("tasks") ? content.get("tasks") : content.get("writingTasks");
            for (var task : maps(source)) {
                if (number(task.get("taskNo")) == 2) result.add("ESSAY");
            }
        }
        return List.copyOf(result);
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String text(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> coverMap(Object value) {
        return value instanceof Map<?, ?> values ? (Map<String, Object>) values : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        return value instanceof List<?> values
                ? values.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList()
                : List.of();
    }

    private Map<String, Object> readMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new BusinessRuleException("Nội dung đề đã xuất bản không hợp lệ");
        }
    }

    private List<String> readStrings(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessRuleException("Không thể tạo bộ lọc danh mục luyện đề");
        }
    }
}
