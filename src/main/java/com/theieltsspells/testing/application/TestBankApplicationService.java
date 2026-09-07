package com.theieltsspells.testing.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.persistence.enums.SkillType;
import com.theieltsspells.shared.web.PageResponse;
import com.theieltsspells.testing.application.dto.TestBankRequest;
import com.theieltsspells.testing.application.dto.TestBankResponse;
import com.theieltsspells.testing.application.dto.TestValidationResponse;
import com.theieltsspells.testing.application.dto.TestVersionResponse;
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
    private static final Set<String> TYPES = Set.of("FULL_TEST", "SINGLE_SKILL");
    private static final Set<String> STATUSES = Set.of("DRAFT", "IN_REVIEW", "PUBLISHED", "ARCHIVED");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TestDraftValidationService validationService;

    public PageResponse<TestBankResponse> list(String query, SkillType skill, String status,
                                               String testType, String format, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        var where = new StringBuilder(" where 1=1");
        var args = new ArrayList<Object>();
        if (query != null && !query.isBlank()) {
            where.append(" and (lower(t.title) like ? or lower(t.code) like ?)");
            var keyword = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            args.add(keyword); args.add(keyword);
        }
        if (skill != null) { where.append(" and t.primary_skill = cast(? as public.skill_type)"); args.add(skill.name()); }
        if (testType != null && !testType.isBlank() && !"ALL".equalsIgnoreCase(testType)) {
            where.append(" and t.test_type = ?");
            args.add(allowed(testType, TYPES));
        }
        if (format != null && !format.isBlank() && !"ALL".equalsIgnoreCase(format)) {
            var normalizedFormat = format.trim().toUpperCase(Locale.ROOT);
            where.append(" and (upper(t.builder_content ->> 'format') = ? or t.tags @> cast(? as jsonb))");
            args.add(normalizedFormat);
            args.add(json(List.of(normalizedFormat)));
        }
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
                  primary_skill, test_type, version, tags, builder_content)
                values (?, ?, ?, 'DRAFT', ?, cast(? as public.skill_type), ?, ?, cast(? as jsonb), cast(? as jsonb))
                returning id
                """, UUID.class, request.title().trim(), blank(request.description()), duration(request), actor,
                request.skill().name(), allowed(request.testType(), TYPES), blankOr(request.version(), "v1.0"),
                json(request.tags() == null ? List.of() : request.tags()),
                json(request.builderContent() == null ? Map.of() : request.builderContent()));
        return get(id);
    }

    @Transactional
    public TestBankResponse update(UUID id, TestBankRequest request, UUID actor, boolean moderator) {
        var current = getLocked(id);
        validate(request);
        assertCanEditDraft(current, id, actor, moderator);
        int expectedRevision = requiredRevision(request.draftRevision());
        int updated = jdbc.update("""
                update public.tests set title=?, description=?, duration_minutes=?,
                  primary_skill=cast(? as public.skill_type), test_type=?,
                  tags=cast(? as jsonb), builder_content=cast(? as jsonb),
                  draft_revision=draft_revision+1, updated_at=now()
                where id=? and draft_revision=?
                """, request.title().trim(), blank(request.description()), duration(request),
                request.skill().name(), allowed(request.testType(), TYPES),
                json(request.tags() == null ? List.of() : request.tags()),
                json(request.builderContent() == null ? Map.of() : request.builderContent()), id, expectedRevision);
        if (updated == 0) throw staleDraft();
        return get(id);
    }

    @Transactional
    public TestBankResponse changeStatus(UUID id, String requestedStatus, int expectedRevision,
                                         UUID actor, boolean moderator) {
        var current = getLocked(id);
        var status = allowed(requestedStatus, STATUSES);
        assertRevision(current, expectedRevision);
        assertTransition(current, status, id, actor, moderator);

        if ("IN_REVIEW".equals(status) || "PUBLISHED".equals(status)) {
            var validation = validationService.validate(current);
            if (!validation.publishable()) {
                throw new BusinessRuleException("Đề còn lỗi cần xử lý trước khi gửi duyệt hoặc xuất bản");
            }
        }

        if ("PUBLISHED".equals(status)) {
            var version = createPublishedVersion(current, actor);
            int updated = jdbc.update("""
                    update public.tests
                    set status=cast(? as public.publish_status), version=?, current_published_version_id=?,
                        draft_revision=draft_revision+1, updated_at=now()
                    where id=? and draft_revision=?
                    """, toDatabaseStatus(status), version.versionLabel(), version.id(), id, expectedRevision);
            if (updated == 0) throw staleDraft();
        } else {
            int updated = jdbc.update("""
                    update public.tests set status=cast(? as public.publish_status),
                      draft_revision=draft_revision+1, updated_at=now()
                    where id=? and draft_revision=?
                    """, toDatabaseStatus(status), id, expectedRevision);
            if (updated == 0) throw staleDraft();
        }
        return get(id);
    }

    @Transactional
    public TestBankResponse createRevision(UUID id, int expectedRevision, UUID actor, boolean moderator) {
        var current = getLocked(id);
        assertRevision(current, expectedRevision);
        if (!"PUBLISHED".equals(current.status()) || current.publishedVersion() == null) {
            throw new BusinessRuleException("Chỉ có thể tạo bản chỉnh sửa từ đề đang xuất bản");
        }
        assertOwnerOrModerator(id, actor, moderator);
        var published = loadPublishedDraft(current.publishedVersion().id());
        String draftVersion = "v" + (current.publishedVersion().versionNumber() + 1) + ".0-draft";
        int updated = jdbc.update("""
                update public.tests
                set title=?, description=?, duration_minutes=?, primary_skill=cast(? as public.skill_type),
                    test_type=?, tags=cast(? as jsonb), builder_content=cast(? as jsonb), version=?,
                    status='DRAFT', draft_revision=draft_revision+1, updated_at=now()
                where id=? and draft_revision=?
                """, published.title(), published.description(), published.durationMinutes(), published.skill().name(),
                published.testType(), published.tagsJson(), published.builderContentJson(), draftVersion,
                id, expectedRevision);
        if (updated == 0) throw staleDraft();
        return get(id);
    }

    public TestValidationResponse validateDraft(UUID id) {
        return validationService.validate(get(id));
    }

    public List<TestVersionResponse> listVersions(UUID id) {
        get(id);
        return jdbc.query("""
                select test_version.id, test_version.version_number, test_version.version_label, test_version.published_at,
                  coalesce(profile.full_name, profile.email, 'Không xác định') published_by
                from public.test_versions test_version
                left join public.profiles profile on profile.id = test_version.published_by
                where test_version.test_id = ?
                order by test_version.version_number desc
                """, (rs, ignored) -> new TestVersionResponse(
                rs.getObject("id", UUID.class), rs.getInt("version_number"), rs.getString("version_label"),
                rs.getObject("published_at", java.time.OffsetDateTime.class), rs.getString("published_by")
        ), id);
    }

    @Transactional
    public void archive(UUID id, UUID actor, boolean moderator) {
        var current = getLocked(id);
        assertOwnerOrModerator(id, actor, moderator);
        if ("PUBLISHED".equals(current.status()) && !moderator) {
            throw new BusinessRuleException("Chỉ Quản lý hoặc Admin mới có thể archive đề đã xuất bản");
        }
        if ("ARCHIVED".equals(current.status())) return;
        jdbc.update("""
                update public.tests set status='ARCHIVED', draft_revision=draft_revision+1, updated_at=now()
                where id=? and draft_revision=?
                """, id, current.draftRevision());
    }

    private String selectSql() {
        return """
                select t.*, coalesce(p.full_name, p.email, 'Không xác định') creator_name,
                  published.id published_version_id, published.version_number published_version_number,
                  published.version_label published_version_label, published.published_at published_at_version,
                  coalesce(publisher.full_name, publisher.email, 'Không xác định') published_by,
                  (select count(*) from public.test_sections s where s.test_id=t.id) section_count,
                  (select count(*) from public.questions q join public.test_sections s on s.id=q.section_id where s.test_id=t.id) question_count,
                  (select count(distinct ta.course_id) from public.test_assignments ta where ta.test_id=t.id) course_count
                from public.tests t
                left join public.profiles p on p.id=t.created_by
                left join public.test_versions published on published.id=t.current_published_version_id
                left join public.profiles publisher on publisher.id=published.published_by
                """;
    }

    private TestBankResponse map(ResultSet rs, int ignored) throws SQLException {
        var status = rs.getString("status");
        var builderContent = readMap(rs.getString("builder_content"));
        int sectionsCount = rs.getInt("section_count");
        int questionsCount = rs.getInt("question_count");
        if (sectionsCount == 0) sectionsCount = draftSectionCount(builderContent);
        if (questionsCount == 0) questionsCount = draftQuestionCount(builderContent);
        return new TestBankResponse(UUID.fromString(rs.getString("id")), rs.getString("code"), rs.getString("title"),
                rs.getString("description"), SkillType.valueOf(rs.getString("primary_skill")),
                rs.getString("test_type"), sectionsCount,
                questionsCount, rs.getInt("duration_minutes"), rs.getString("version"),
                "SCHEDULED".equals(status) ? "IN_REVIEW" : status, readList(rs.getString("tags")),
                rs.getInt("course_count"), rs.getString("creator_name"),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class), builderContent,
                rs.getInt("draft_revision"), publishedVersion(rs));
    }

    private void validate(TestBankRequest request) {
        allowed(request.testType(), TYPES);
        if (request.skill() == SkillType.GENERAL || request.skill() == SkillType.VOCABULARY)
            throw new BusinessRuleException("Đề thi phải thuộc một trong bốn kỹ năng IELTS");
    }
    private TestBankResponse getLocked(UUID id) {
        var values = jdbc.query(selectSql() + " where t.id = ? for update of t", this::map, id);
        if (values.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy đề thi");
        return values.getFirst();
    }
    private void assertCanEditDraft(TestBankResponse current, UUID id, UUID actor, boolean moderator) {
        if (!"DRAFT".equals(current.status())) {
            throw new ConflictException("Đề không ở trạng thái nháp. Hãy tạo bản chỉnh sửa từ phiên bản đang xuất bản.");
        }
        assertOwnerOrModerator(id, actor, moderator);
    }
    private void assertOwnerOrModerator(UUID id, UUID actor, boolean moderator) {
        if (moderator) return;
        UUID createdBy = jdbc.queryForObject("select created_by from public.tests where id=?", UUID.class, id);
        if (!actor.equals(createdBy)) throw new BusinessRuleException("Bạn không có quyền chỉnh sửa đề này");
    }
    private void assertRevision(TestBankResponse current, int expectedRevision) {
        if (current.draftRevision() != expectedRevision) throw staleDraft();
    }
    private int requiredRevision(Integer revision) {
        if (revision == null || revision < 1) {
            throw new BusinessRuleException("Thiếu phiên bản nháp khi lưu. Hãy tải lại đề và thử lại.");
        }
        return revision;
    }
    private ConflictException staleDraft() {
        return new ConflictException("Nội dung đã được cập nhật ở nơi khác. Hãy tải lại đề trước khi tiếp tục.");
    }
    private void assertTransition(TestBankResponse current, String target, UUID id, UUID actor, boolean moderator) {
        String source = current.status();
        if (source.equals(target)) throw new BusinessRuleException("Đề đã ở trạng thái này");
        if ("ARCHIVED".equals(source)) throw new BusinessRuleException("Đề đã archive không thể đổi trạng thái");
        if ("IN_REVIEW".equals(target)) {
            if (!"DRAFT".equals(source)) throw new BusinessRuleException("Chỉ nháp mới có thể gửi duyệt");
            assertOwnerOrModerator(id, actor, moderator);
            return;
        }
        if ("DRAFT".equals(target)) {
            if (!"IN_REVIEW".equals(source) || !moderator) {
                throw new BusinessRuleException("Chỉ Quản lý hoặc Admin mới có thể trả đề về nháp");
            }
            return;
        }
        if ("PUBLISHED".equals(target)) {
            if (!("DRAFT".equals(source) || "IN_REVIEW".equals(source)) || !moderator) {
                throw new BusinessRuleException("Chỉ Quản lý hoặc Admin mới có thể xuất bản đề");
            }
            return;
        }
        if ("ARCHIVED".equals(target)) {
            assertOwnerOrModerator(id, actor, moderator);
            if ("PUBLISHED".equals(source) && !moderator) {
                throw new BusinessRuleException("Chỉ Quản lý hoặc Admin mới có thể archive đề đã xuất bản");
            }
        }
    }
    private TestVersionResponse createPublishedVersion(TestBankResponse test, UUID actor) {
        int number = jdbc.queryForObject("select coalesce(max(version_number), 0) + 1 from public.test_versions where test_id=?", Integer.class, test.id());
        String label = "v" + number + ".0";
        UUID id = jdbc.queryForObject("""
                insert into public.test_versions(
                  test_id, version_number, version_label, title, description, duration_minutes,
                  primary_skill, test_type, tags, builder_content, published_by
                ) values (?, ?, ?, ?, ?, ?, cast(? as public.skill_type), ?, cast(? as jsonb), cast(? as jsonb), ?)
                returning id
                """, UUID.class, test.id(), number, label, test.title(), test.description(), test.durationMinutes(),
                test.skill().name(), test.testType(), json(test.tags()), json(test.builderContent()), actor);
        return new TestVersionResponse(id, number, label, java.time.OffsetDateTime.now(), "");
    }
    private PublishedDraft loadPublishedDraft(UUID versionId) {
        var values = jdbc.query("""
                select title, description, duration_minutes, primary_skill, test_type, tags::text tags_json,
                  builder_content::text builder_content_json
                from public.test_versions where id=?
                """, (rs, ignored) -> new PublishedDraft(
                rs.getString("title"), rs.getString("description"), rs.getInt("duration_minutes"),
                SkillType.valueOf(rs.getString("primary_skill")), rs.getString("test_type"),
                rs.getString("tags_json"), rs.getString("builder_content_json")
        ), versionId);
        if (values.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy phiên bản đã xuất bản");
        return values.getFirst();
    }
    private TestVersionResponse publishedVersion(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("published_version_id", UUID.class);
        if (id == null) return null;
        return new TestVersionResponse(id, rs.getInt("published_version_number"), rs.getString("published_version_label"),
                rs.getObject("published_at_version", java.time.OffsetDateTime.class), rs.getString("published_by"));
    }
    private record PublishedDraft(String title, String description, int durationMinutes, SkillType skill,
                                 String testType, String tagsJson, String builderContentJson) { }
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

    private int draftSectionCount(Map<String, Object> content) {
        var passages = content.get("passages");
        if (passages instanceof List<?> items) return Math.toIntExact(items.stream().filter(Map.class::isInstance).count());
        var listeningParts = content.get("listeningParts");
        if (listeningParts instanceof List<?> items) return Math.toIntExact(items.stream().filter(Map.class::isInstance).count());
        var writingTasks = content.get("writingTasks");
        if (writingTasks instanceof List<?> items) return Math.toIntExact(items.stream().filter(Map.class::isInstance).count());
        var speakingParts = content.get("speakingParts");
        if (speakingParts instanceof List<?> items) return Math.toIntExact(items.stream().filter(Map.class::isInstance).count());
        var passageContent = content.get("passageContent");
        if (passageContent instanceof Map<?, ?> values) {
            return Math.toIntExact(values.values().stream().filter(value -> value != null && !value.toString().isBlank()).count());
        }
        var questionGroups = content.get("questionGroups");
        if (questionGroups instanceof List<?> items && !items.isEmpty()) return 1;
        var sourceTestIds = content.get("sourceTestIds");
        if (sourceTestIds instanceof List<?> items) return items.size();
        return 0;
    }

    private int draftQuestionCount(Map<String, Object> content) {
        int fromPassages = countQuestionsInSections(content.get("passages"));
        if (fromPassages > 0) return fromPassages;
        int fromListening = countQuestionsInSections(content.get("listeningParts"));
        if (fromListening > 0) return fromListening;
        return countQuestionsInGroups(content.get("questionGroups"));
    }

    private int countQuestionsInSections(Object sections) {
        if (!(sections instanceof List<?> items)) return 0;
        int total = 0;
        for (var item : items) {
            if (item instanceof Map<?, ?> section) total += countQuestionsInGroups(section.get("questionGroups"));
        }
        return total;
    }

    private int countQuestionsInGroups(Object groups) {
        if (!(groups instanceof List<?> items)) return 0;
        int total = 0;
        for (var item : items) {
            if (item instanceof Map<?, ?> group && group.get("questions") instanceof List<?> questions) total += questions.size();
        }
        return total;
    }

    private boolean hasDraftQuestions(Map<String, Object> content) {
        return draftQuestionCount(content) > 0;
    }

    private boolean hasDraftContent(Map<String, Object> content) {
        if (hasDraftQuestions(content)) return true;
        if (hasText(content.get("promptText")) || hasText(content.get("transcriptText")) || hasText(content.get("sampleAnswer")))
            return true;
        return countNonEmptyTextSections(content.get("writingTasks"), "promptHtml") > 0
                || countNonEmptyTextSections(content.get("speakingParts"), "cueCardPromptHtml") > 0
                || countNonEmptyTextSections(content.get("listeningParts"), "transcriptHtml") > 0;
    }

    private int countNonEmptyTextSections(Object sections, String textKey) {
        if (!(sections instanceof List<?> items)) return 0;
        int total = 0;
        for (var item : items) {
            if (item instanceof Map<?, ?> section && hasText(section.get(textKey))) total++;
        }
        return total;
    }

    private boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }
}
