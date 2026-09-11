package com.theieltsspells.testing.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.academic.application.AcademicMembershipService;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.persistence.enums.SkillType;
import com.theieltsspells.testing.application.dto.CreateTestAssignmentRequest;
import com.theieltsspells.testing.application.dto.TestAssignmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestAssignmentApplicationService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AcademicMembershipService memberships;
    private final ReadingVersionMaterializer readingVersionMaterializer;

    @Transactional
    public TestAssignmentResponse create(CreateTestAssignmentRequest request, UUID actor, boolean moderator) {
        validateSchedule(request.opensAt(), request.closesAt());
        if (!memberships.courseExists(request.courseId())) {
            throw new ResourceNotFoundException("Không tìm thấy khóa học");
        }
        if (!moderator && !memberships.isTeacherAssigned(request.courseId(), actor)) {
            throw new BusinessRuleException("Bạn không được phân công cho khóa học này");
        }

        var version = loadVersion(request.testVersionId());
        if (version.skill() != SkillType.READING) {
            throw new BusinessRuleException("Reading delivery hiện chỉ hỗ trợ đề Reading đã xuất bản");
        }
        readingVersionMaterializer.ensureMaterialized(version.id(), version.builderContent());

        Integer existing = jdbc.queryForObject("""
                select count(*) from public.test_assignments
                where test_version_id = ? and course_id = ? and archived_at is null
                """, Integer.class, version.id(), request.courseId());
        if (existing != null && existing > 0) {
            throw new ConflictException("Phiên bản đề này đã được giao cho khóa học");
        }

        UUID id;
        try {
            id = jdbc.queryForObject("""
                    insert into public.test_assignments(
                      test_id, test_version_id, course_id, assigned_by, opens_at, closes_at,
                      max_attempts, mode, duration_seconds, show_result_after_submit
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    returning id
                    """, UUID.class, version.testId(), version.id(), request.courseId(), actor,
                    request.opensAt(), request.closesAt(), request.maxAttempts(), mode(request.mode()),
                    request.durationSeconds(), request.showResultAfterSubmit() == null || request.showResultAfterSubmit());
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Phiên bản đề này đã được giao cho khóa học");
        }
        return get(id);
    }

    public TestAssignmentResponse get(UUID assignmentId) {
        var values = jdbc.query(selectSql() + " where assignment.id = ?", this::map, assignmentId);
        if (values.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy bài kiểm tra được giao");
        }
        return values.getFirst();
    }

    public List<TestAssignmentResponse> list(UUID courseId, UUID actor, boolean moderator) {
        if (!memberships.courseExists(courseId)) {
            throw new ResourceNotFoundException("Không tìm thấy khóa học");
        }
        if (!moderator && !memberships.isTeacherAssigned(courseId, actor)) {
            throw new BusinessRuleException("Bạn không được phân công cho khóa học này");
        }
        return jdbc.query(selectSql() + " where assignment.course_id = ? order by assignment.created_at desc", this::map, courseId);
    }

    @Transactional
    public void archive(UUID assignmentId, UUID actor, boolean moderator) {
        var assignment = get(assignmentId);
        if (!moderator && !memberships.isTeacherAssigned(assignment.courseId(), actor)) {
            throw new BusinessRuleException("Bạn không được phân công cho khóa học này");
        }
        if (assignment.archivedAt() != null) {
            return;
        }
        jdbc.update("update public.test_assignments set archived_at = now(), updated_at = now() where id = ?", assignmentId);
    }

    private VersionForAssignment loadVersion(UUID versionId) {
        var values = jdbc.query("""
                select id, test_id, title, duration_minutes, primary_skill, builder_content::text builder_content
                from public.test_versions where id = ?
                """, (rs, ignored) -> new VersionForAssignment(
                rs.getObject("id", UUID.class), rs.getObject("test_id", UUID.class), rs.getString("title"),
                rs.getInt("duration_minutes"), SkillType.valueOf(rs.getString("primary_skill")),
                readMap(rs.getString("builder_content"))
        ), versionId);
        if (values.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy phiên bản đề đã xuất bản");
        }
        return values.getFirst();
    }

    private String selectSql() {
        return """
                select assignment.id, assignment.test_id, assignment.test_version_id, assignment.course_id,
                  assignment.mode, assignment.opens_at, assignment.closes_at, assignment.max_attempts,
                  assignment.duration_seconds, assignment.show_result_after_submit, assignment.archived_at,
                  assignment.created_at, version.title test_title, version.version_label,
                  version.primary_skill, course.name course_name
                from public.test_assignments assignment
                join public.test_versions version on version.id = assignment.test_version_id
                join public.courses course on course.id = assignment.course_id
                """;
    }

    private TestAssignmentResponse map(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        return new TestAssignmentResponse(
                rs.getObject("id", UUID.class), rs.getObject("test_id", UUID.class),
                rs.getObject("test_version_id", UUID.class), rs.getString("test_title"),
                rs.getString("version_label"), rs.getString("primary_skill"),
                rs.getObject("course_id", UUID.class), rs.getString("course_name"), rs.getString("mode"),
                rs.getObject("opens_at", OffsetDateTime.class), rs.getObject("closes_at", OffsetDateTime.class),
                rs.getShort("max_attempts"), rs.getObject("duration_seconds", Integer.class),
                rs.getBoolean("show_result_after_submit"), rs.getObject("archived_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private void validateSchedule(OffsetDateTime opensAt, OffsetDateTime closesAt) {
        if (opensAt != null && closesAt != null && !closesAt.isAfter(opensAt)) {
            throw new BusinessRuleException("Thời điểm đóng bài phải sau thời điểm mở bài");
        }
    }

    private String mode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> readMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new BusinessRuleException("Nội dung phiên bản đề không hợp lệ");
        }
    }

    private record VersionForAssignment(UUID id, UUID testId, String title, int durationMinutes,
                                        SkillType skill, Map<String, Object> builderContent) {
    }
}
