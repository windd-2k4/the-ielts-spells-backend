package com.theieltsspells.testing.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.persistence.enums.SkillType;
import com.theieltsspells.shared.web.PageResponse;
import com.theieltsspells.testing.application.dto.TestBankRequest;
import com.theieltsspells.testing.application.dto.TestBankResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestBankApplicationService {
    private static final Set<String> PURPOSES = Set.of("PLACEMENT", "PRACTICE", "PROGRESS", "MOCK_TEST");
    private static final Set<String> TYPES = Set.of("FULL_TEST", "SINGLE_SKILL");
    private static final Set<String> STATUSES = Set.of("DRAFT", "IN_REVIEW", "PUBLISHED", "ARCHIVED");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PageResponse<TestBankResponse> list(String query, String purpose, SkillType skill, String status,
                                               int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        var where = new StringBuilder(" where 1=1");
        var args = new ArrayList<Object>();
        if (query != null && !query.isBlank()) {
            where.append(" and (lower(t.title) like ? or lower(t.code) like ?)");
            var keyword = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            args.add(keyword); args.add(keyword);
        }
        if (purpose != null && !purpose.isBlank()) { where.append(" and t.purpose = ?"); args.add(allowed(purpose, PURPOSES)); }
        if (skill != null) { where.append(" and t.primary_skill = cast(? as public.skill_type)"); args.add(skill.name()); }
        if (status != null && !status.isBlank()) {
            where.append(" and t.status = cast(? as public.publish_status)"); args.add(toDatabaseStatus(status));
        } else where.append(" and t.status <> 'ARCHIVED'");
        var total = jdbc.queryForObject("select count(*) from public.tests t" + where, Long.class, args.toArray());
        var dataArgs = new ArrayList<>(args); dataArgs.add(safeSize); dataArgs.add(safePage * safeSize);
        var content = jdbc.query(selectSql() + where + " order by t.updated_at desc limit ? offset ?", this::map, dataArgs.toArray());
        long count = total == null ? 0 : total;
        int totalPages = (int) Math.ceil((double) count / safeSize);
        return new PageResponse<>(content, safePage, safeSize, count, totalPages, safePage == 0, safePage + 1 >= totalPages);
    }

    public TestBankResponse get(UUID id) {
        var values = jdbc.query(selectSql() + " where t.id = ?", this::map, id);
        if (values.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy đề thi");
        return values.getFirst();
    }

    @Transactional
    public TestBankResponse create(TestBankRequest request, UUID actor) {
        validate(request);
        UUID id = jdbc.queryForObject("""
                insert into public.tests(title, description, duration_minutes, status, created_by,
                  purpose, primary_skill, test_type, difficulty, version, tags, builder_content)
                values (?, ?, ?, 'DRAFT', ?, ?, cast(? as public.skill_type), ?, ?, ?, cast(? as jsonb), cast(? as jsonb))
                returning id
                """, UUID.class, request.title().trim(), blank(request.description()), duration(request), actor,
                allowed(request.purpose(), PURPOSES), request.skill().name(), allowed(request.testType(), TYPES),
                blankOr(request.difficulty(), "Chưa phân loại"), blankOr(request.version(), "v1.0"),
                json(request.tags() == null ? List.of() : request.tags()),
                json(request.builderContent() == null ? Map.of() : request.builderContent()));
        return get(id);
    }

    @Transactional
    public TestBankResponse update(UUID id, TestBankRequest request) {
        get(id); validate(request);
        jdbc.update("""
                update public.tests set title=?, description=?, duration_minutes=?, purpose=?,
                  primary_skill=cast(? as public.skill_type), test_type=?, difficulty=?, version=?,
                  tags=cast(? as jsonb), builder_content=cast(? as jsonb), updated_at=now()
                where id=?
                """, request.title().trim(), blank(request.description()), duration(request),
                allowed(request.purpose(), PURPOSES), request.skill().name(), allowed(request.testType(), TYPES),
                blankOr(request.difficulty(), "Chưa phân loại"), blankOr(request.version(), "v1.0"),
                json(request.tags() == null ? List.of() : request.tags()),
                json(request.builderContent() == null ? Map.of() : request.builderContent()), id);
        return get(id);
    }

    @Transactional
    public TestBankResponse changeStatus(UUID id, String requestedStatus) {
        var current = get(id);
        var status = allowed(requestedStatus, STATUSES);
        if ("PUBLISHED".equals(status) && current.totalQuestions() == 0 && current.builderContent().isEmpty())
            throw new BusinessRuleException("Đề thi chưa có nội dung nên chưa thể xuất bản");
        jdbc.update("update public.tests set status=cast(? as public.publish_status), updated_at=now() where id=?",
                toDatabaseStatus(status), id);
        return get(id);
    }

    @Transactional
    public void archive(UUID id) { changeStatus(id, "ARCHIVED"); }

    private String selectSql() {
        return """
                select t.*, coalesce(p.full_name, p.email, 'Không xác định') creator_name,
                  (select count(*) from public.test_sections s where s.test_id=t.id) section_count,
                  (select count(*) from public.questions q join public.test_sections s on s.id=q.section_id where s.test_id=t.id) question_count,
                  (select count(distinct ta.course_id) from public.test_assignments ta where ta.test_id=t.id) course_count
                from public.tests t left join public.profiles p on p.id=t.created_by
                """;
    }

    private TestBankResponse map(ResultSet rs, int ignored) throws SQLException {
        var status = rs.getString("status");
        return new TestBankResponse(UUID.fromString(rs.getString("id")), rs.getString("code"), rs.getString("title"),
                rs.getString("description"), rs.getString("purpose"), SkillType.valueOf(rs.getString("primary_skill")),
                rs.getString("test_type"), rs.getString("difficulty"), rs.getInt("section_count"),
                rs.getInt("question_count"), rs.getInt("duration_minutes"), rs.getString("version"),
                "SCHEDULED".equals(status) ? "IN_REVIEW" : status, readList(rs.getString("tags")),
                rs.getInt("course_count"), rs.getString("creator_name"),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class), readMap(rs.getString("builder_content")));
    }

    private void validate(TestBankRequest request) {
        allowed(request.purpose(), PURPOSES); allowed(request.testType(), TYPES);
        if (request.skill() == SkillType.GENERAL || request.skill() == SkillType.VOCABULARY)
            throw new BusinessRuleException("Đề thi phải thuộc một trong bốn kỹ năng IELTS");
    }
    private int duration(TestBankRequest request) { return request.durationMinutes() == null ? 60 : request.durationMinutes(); }
    private String toDatabaseStatus(String value) { var normalized = allowed(value, STATUSES); return "IN_REVIEW".equals(normalized) ? "SCHEDULED" : normalized; }
    private String allowed(String value, Set<String> values) {
        var normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!values.contains(normalized)) throw new BusinessRuleException("Giá trị không hợp lệ: " + value);
        return normalized;
    }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String blankOr(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException e) { throw new BusinessRuleException("Nội dung JSON không hợp lệ"); } }
    private List<String> readList(String value) { try { return objectMapper.readValue(value, new TypeReference<>() {}); } catch (Exception e) { return List.of(); } }
    private Map<String, Object> readMap(String value) { try { return objectMapper.readValue(value, new TypeReference<>() {}); } catch (Exception e) { return Map.of(); } }
}
