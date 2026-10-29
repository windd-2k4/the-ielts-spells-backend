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
        if (request.sessionId() != null && memberships.session(request.courseId(), request.sessionId()).isEmpty()) {
            throw new BusinessRuleException("Session không thuộc khóa học đã chọn");
        }

        var version = loadVersion(request.testVersionId());
        String skillPair = jdbc.queryForObject(
                "select skill_pair::text from public.courses where id = ?", String.class, request.courseId());
        boolean speakingWriting = version.skill() == SkillType.SPEAKING || version.skill() == SkillType.WRITING;
        if (speakingWriting != "SPEAKING_WRITING".equals(skillPair)) {
            throw new BusinessRuleException("Kỹ năng của đề không khớp với loại khóa học");
        }
        if (version.skill() == SkillType.READING) {
            readingVersionMaterializer.ensureMaterialized(version.id(), version.builderContent());
        }

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
                      test_id, test_version_id, course_id, session_id, assigned_by, opens_at, closes_at,
                      max_attempts, mode, duration_seconds, show_result_after_submit
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    returning id
                    """, UUID.class, version.testId(), version.id(), request.courseId(), request.sessionId(), actor,
                    request.opensAt(), request.closesAt(), request.maxAttempts(), mode(request.mode()),
                    request.durationSeconds(), request.showResultAfterSubmit() == null || request.showResultAfterSubmit());
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Phiên bản đề này đã được giao cho khóa học");
        }
        if (request.sessionId() != null) {
            linkToCourseActivity(id, request.courseId(), request.sessionId(), version.skill().name());
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
                  version.primary_skill, course.name course_name, assignment.session_id,
                  session.session_no, session.title session_title
                from public.test_assignments assignment
                join public.test_versions version on version.id = assignment.test_version_id
                join public.courses course on course.id = assignment.course_id
                left join public.course_sessions session on session.id = assignment.session_id
                """;
    }

    private TestAssignmentResponse map(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        return new TestAssignmentResponse(
                rs.getObject("id", UUID.class), rs.getObject("test_id", UUID.class),
                rs.getObject("test_version_id", UUID.class), rs.getString("test_title"),
                rs.getString("version_label"), rs.getString("primary_skill"),
                rs.getObject("course_id", UUID.class), rs.getString("course_name"),
                rs.getObject("session_id", UUID.class), rs.getObject("session_no", Short.class),
                rs.getString("session_title"), rs.getString("mode"),
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

    /**
     * Preserve the existing Course -> Session -> CourseActivity -> TestAssignment
     * graph when a matching published activity already exists. The attempt tables
     * remain the only evidence source; this update only attaches the assignment.
     */
    private void linkToCourseActivity(UUID assignmentId, UUID courseId, UUID sessionId, String skill) {
        var activityIds = jdbc.query("""
                select activity.id
                from public.course_activities activity
                join public.learning_activities learning on learning.id = activity.activity_id
                where activity.course_id = ? and activity.session_id = ?
                  and activity.is_published = true and activity.test_assignment_id is null
                  and learning.skill = cast(? as public.skill_type)
                order by activity.created_at, activity.id
                limit 1
                """, (rs, ignored) -> rs.getObject("id", UUID.class), courseId, sessionId, skill);
        if (!activityIds.isEmpty()) {
            jdbc.update("""
                    update public.course_activities
                    set test_assignment_id = ?, opens_at = coalesce(opens_at, (select opens_at from public.test_assignments where id = ?)),
                        due_at = coalesce(due_at, (select closes_at from public.test_assignments where id = ?)), updated_at = now()
                    where id = ? and test_assignment_id is null
                    """, assignmentId, assignmentId, assignmentId, activityIds.getFirst());
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
