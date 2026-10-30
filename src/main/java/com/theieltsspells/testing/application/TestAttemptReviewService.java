package com.theieltsspells.testing.application;

import com.theieltsspells.academic.application.AcademicMembershipService;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.testing.application.dto.StudentReadingAttemptResponse;
import com.theieltsspells.testing.application.dto.BulkTestAssignmentReminderRequest;
import com.theieltsspells.testing.application.dto.BulkTestAssignmentReminderResponse;
import com.theieltsspells.testing.application.dto.TestAttemptInsightsResponse;
import com.theieltsspells.testing.application.dto.TestAttemptReviewResponse;
import com.theieltsspells.testing.application.dto.TestAttemptSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.theieltsspells.notification.WritingNotificationService;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestAttemptReviewService {

    private final JdbcTemplate jdbc;
    private final AcademicMembershipService memberships;
    private final StudentReadingDeliveryService delivery;
    private final TestAttemptQuestionReviewService questionReviews;
    private final WritingNotificationService notifications;

    public List<TestAttemptSummaryResponse> list(UUID courseId, UUID sessionId, String skill,
                                                 UUID actor, boolean moderator) {
        assertCanRead(courseId, actor, moderator);
        var sql = new StringBuilder(summarySql());
        sql.append(" where assignment.course_id = ?");
        var args = new ArrayList<Object>();
        args.add(courseId);
        if (sessionId != null) {
            sql.append(" and assignment.session_id = ?");
            args.add(sessionId);
        }
        if (skill != null && !skill.isBlank()) {
            sql.append(" and version.primary_skill = cast(? as public.skill_type)");
            args.add(skill.trim().toUpperCase());
        }
        sql.append(" order by profile.full_name, student.student_code, version.title, attempt.attempt_no desc");
        return jdbc.query(sql.toString(), this::mapSummary, args.toArray());
    }

    public TestAttemptReviewResponse detail(UUID courseId, UUID attemptId,
                                            UUID actor, boolean moderator) {
        assertCanRead(courseId, actor, moderator);
        var summary = findSummary(courseId, attemptId);
        var history = listForStudent(courseId, summary.studentId(), summary.assignmentId(), actor, moderator);
        StudentReadingAttemptResponse payload = delivery.getAdminAttempt(attemptId, courseId);
        var result = "IN_PROGRESS".equals(summary.status()) ? null : delivery.getAdminResult(attemptId, courseId);
        return new TestAttemptReviewResponse(summary, history, payload, result, questionReviews.listForAttempt(attemptId));
    }

    public TestAttemptInsightsResponse insights(UUID courseId, UUID sessionId, String skill,
                                                UUID actor, boolean moderator) {
        assertCanRead(courseId, actor, moderator);
        var questionTypes = jdbc.query("""
                select question.type_format,
                  count(*) filter (where response.is_correct = false) wrong_count,
                  count(*) filter (where response.is_correct is not null) answered_count,
                  count(review.id) reviewed_count
                from public.test_attempts attempt
                join public.test_assignments assignment on assignment.id = attempt.test_assignment_id
                join public.test_versions version on version.id = attempt.test_version_id
                join public.test_version_questions question on question.test_version_id = version.id
                left join public.test_attempt_responses response
                  on response.attempt_id = attempt.id and response.question_key = question.question_key
                left join public.test_attempt_question_reviews review
                  on review.attempt_id = attempt.id and review.question_key = question.question_key
                where assignment.course_id = ?
                  and attempt.attempt_origin = 'ASSIGNMENT'
                  and attempt.status <> 'IN_PROGRESS'
                """ + optionalInsightFilters(sessionId, skill) + """
                group by question.type_format
                order by wrong_count desc, question.type_format
                """, (rs, ignored) -> new TestAttemptInsightsResponse.QuestionTypeInsight(
                rs.getString("type_format"), rs.getInt("wrong_count"),
                rs.getInt("answered_count"), rs.getInt("reviewed_count")
        ), insightArgs(courseId, sessionId, skill).toArray());

        var reasons = jdbc.query("""
                select review.reason_code, count(*) reason_count
                from public.test_attempt_question_reviews review
                join public.test_attempts attempt on attempt.id = review.attempt_id
                join public.test_assignments assignment on assignment.id = attempt.test_assignment_id
                join public.test_versions version on version.id = attempt.test_version_id
                where assignment.course_id = ?
                  and attempt.attempt_origin = 'ASSIGNMENT'
                  and attempt.status <> 'IN_PROGRESS'
                """ + optionalInsightFilters(sessionId, skill) + """
                group by review.reason_code
                order by reason_count desc, review.reason_code
                """, (rs, ignored) -> new TestAttemptInsightsResponse.ReasonInsight(
                rs.getString("reason_code"), rs.getInt("reason_count")
        ), insightArgs(courseId, sessionId, skill).toArray());

        Long pending = jdbc.queryForObject("""
                select count(*)
                from public.test_attempts attempt
                join public.test_assignments assignment on assignment.id = attempt.test_assignment_id
                join public.test_versions version on version.id = attempt.test_version_id
                join public.test_version_questions question on question.test_version_id = version.id
                left join public.test_attempt_responses response
                  on response.attempt_id = attempt.id and response.question_key = question.question_key
                left join public.test_attempt_question_reviews review
                  on review.attempt_id = attempt.id and review.question_key = question.question_key
                where assignment.course_id = ?
                  and attempt.attempt_origin = 'ASSIGNMENT'
                  and attempt.status <> 'IN_PROGRESS'
                  and (response.id is null or response.is_correct = false)
                  and review.id is null
                """ + optionalInsightFilters(sessionId, skill), Long.class,
                insightArgs(courseId, sessionId, skill).toArray());

        var trends = jdbc.query("""
                select attempt.student_id, student.student_code,
                  coalesce(profile.full_name, profile.email, 'Học viên') student_name,
                  version.title test_title, attempt.attempt_no,
                  coalesce(attempt.final_score, attempt.auto_score) score,
                  coalesce(sum(question.max_score), 0) max_score,
                  attempt.submitted_at
                from public.test_attempts attempt
                join public.test_assignments assignment on assignment.id = attempt.test_assignment_id
                join public.test_versions version on version.id = attempt.test_version_id
                join public.student_profiles student on student.user_id = attempt.student_id
                join public.profiles profile on profile.id = student.user_id
                join public.test_version_questions question on question.test_version_id = version.id
                where assignment.course_id = ?
                  and attempt.attempt_origin = 'ASSIGNMENT'
                  and attempt.status <> 'IN_PROGRESS'
                """ + optionalInsightFilters(sessionId, skill) + """
                group by attempt.student_id, student.student_code, profile.full_name, profile.email,
                  version.title, attempt.attempt_no, attempt.final_score, attempt.auto_score, attempt.submitted_at
                order by student_name, version.title, attempt.attempt_no
                """, (rs, ignored) -> new TestAttemptInsightsResponse.AttemptTrend(
                rs.getObject("student_id", UUID.class), rs.getString("student_code"),
                rs.getString("student_name"), rs.getString("test_title"), rs.getShort("attempt_no"),
                rs.getBigDecimal("score"), rs.getBigDecimal("max_score"),
                rs.getObject("submitted_at", OffsetDateTime.class)
        ), insightArgs(courseId, sessionId, skill).toArray());

        var listeningParts = jdbc.query("""
                select section.section_no, section.title,
                  count(*) filter (where response.is_correct = false) wrong_count,
                  count(*) filter (where response.is_correct is not null) answered_count,
                  count(review.id) reviewed_count
                from public.test_attempts attempt
                join public.test_assignments assignment on assignment.id = attempt.test_assignment_id
                join public.test_versions version on version.id = attempt.test_version_id
                join public.test_version_questions question on question.test_version_id = version.id
                join public.test_version_question_groups question_group on question_group.id = question.group_id
                join public.test_version_sections section on section.id = question_group.section_id
                left join public.test_attempt_responses response
                  on response.attempt_id = attempt.id and response.question_key = question.question_key
                left join public.test_attempt_question_reviews review
                  on review.attempt_id = attempt.id and review.question_key = question.question_key
                where assignment.course_id = ?
                  and attempt.attempt_origin = 'ASSIGNMENT'
                  and version.primary_skill = 'LISTENING'
                  and attempt.status <> 'IN_PROGRESS'
                """ + optionalInsightFilters(sessionId, skill) + """
                group by section.section_no, section.title
                order by section.section_no
                """, (rs, ignored) -> new TestAttemptInsightsResponse.ListeningPartInsight(
                rs.getInt("section_no"), rs.getString("title"), rs.getInt("wrong_count"),
                rs.getInt("answered_count"), rs.getInt("reviewed_count")
        ), insightArgs(courseId, sessionId, skill).toArray());

        return new TestAttemptInsightsResponse(questionTypes, reasons, trends,
                pending == null ? 0 : pending.intValue(), listeningParts);
    }

    @Transactional
    public BulkTestAssignmentReminderResponse sendTestAssignmentReminder(UUID courseId,
                                                                          BulkTestAssignmentReminderRequest request,
                                                                          UUID actor,
                                                                          boolean moderator) {
        assertCanRead(courseId, actor, moderator);
        var assignments = jdbc.query("""
                select assignment.id, version.title
                from public.test_assignments assignment
                join public.test_versions version on version.id = assignment.test_version_id
                where assignment.id = ? and assignment.course_id = ? and assignment.archived_at is null
                """, (rs, ignored) -> new AssignmentReminderContext(
                rs.getObject("id", UUID.class), rs.getString("title")
        ), request.testAssignmentId(), courseId);
        if (assignments.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy test assignment trong khóa học");
        }
        var assignment = assignments.getFirst();
        var requested = request.studentIds() == null ? java.util.Set.<UUID>of()
                : new java.util.HashSet<>(request.studentIds());
        var recipients = jdbc.query("""
                select enrollment.student_id
                from public.enrollments enrollment
                where enrollment.course_id = ? and enrollment.status = 'ACTIVE'
                order by enrollment.student_id
                """, (rs, ignored) -> rs.getObject("student_id", UUID.class), courseId).stream()
                .filter(studentId -> requested.isEmpty() || requested.contains(studentId))
                .toList();
        String message = request.message() == null || request.message().isBlank()
                ? "Bạn có bài " + assignment.title() + " cần hoàn thành trên hệ thống."
                : request.message().trim();
        int notificationsCreated = 0;
        for (UUID studentId : recipients) {
            if (!notifications.enabled(studentId, "ASSIGNMENT_REMINDER")) continue;
            jdbc.update("""
                    insert into public.notifications(user_id, type, title, body, data)
                    values (?, 'ASSIGNMENT_REMINDER', ?, ?, jsonb_build_object(
                      'testAssignmentId', cast(? as text), 'courseId', cast(? as text)))
                    """, studentId, "Nhắc hoàn thành bài: " + assignment.title(), message,
                    assignment.id(), courseId);
            notificationsCreated++;
        }
        return new BulkTestAssignmentReminderResponse(recipients.size(), notificationsCreated);
    }

    private String optionalInsightFilters(UUID sessionId, String skill) {
        var sql = new StringBuilder();
        if (sessionId != null) sql.append(" and assignment.session_id = ?");
        if (skill != null && !skill.isBlank()) {
            sql.append(" and version.primary_skill = cast(? as public.skill_type)");
        }
        return sql.toString();
    }

    private List<Object> insightArgs(UUID courseId, UUID sessionId, String skill) {
        var args = new ArrayList<Object>();
        args.add(courseId);
        if (sessionId != null) args.add(sessionId);
        if (skill != null && !skill.isBlank()) args.add(skill.trim().toUpperCase());
        return args;
    }

    private record AssignmentReminderContext(UUID id, String title) {
    }

    private List<TestAttemptSummaryResponse> listForStudent(UUID courseId, UUID studentId, UUID assignmentId,
                                                            UUID actor, boolean moderator) {
        var sql = summarySql() + " where assignment.course_id = ? and attempt.student_id = ? and attempt.test_assignment_id = ?"
                + " order by version.title, attempt.attempt_no desc";
        return jdbc.query(sql, this::mapSummary, courseId, studentId, assignmentId);
    }

    private TestAttemptSummaryResponse findSummary(UUID courseId, UUID attemptId) {
        var values = jdbc.query(summarySql() + " where assignment.course_id = ? and attempt.id = ?",
                this::mapSummary, courseId, attemptId);
        if (values.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy lượt làm bài trong khóa học");
        }
        return values.getFirst();
    }

    private String summarySql() {
        return """
                select attempt.id attempt_id, attempt.test_assignment_id assignment_id,
                  attempt.test_version_id, assignment.course_id, assignment.session_id,
                  session.session_no, session.title session_title, attempt.student_id,
                  student.student_code, coalesce(profile.full_name, profile.email, 'Học viên') student_name,
                  version.title test_title, version.version_label, version.primary_skill,
                  attempt.attempt_no, attempt.status, attempt.started_at, attempt.submitted_at,
                  greatest(0, extract(epoch from (coalesce(attempt.submitted_at, now()) - attempt.started_at)))::bigint duration_seconds,
                  coalesce(attempt.final_score, attempt.auto_score) score,
                  (select coalesce(sum(question.max_score), 0) from public.test_version_questions question
                    where question.test_version_id = attempt.test_version_id) max_score,
                  (select count(*) from public.test_attempt_responses response
                    where response.attempt_id = attempt.id and response.is_correct = true) correct_count,
                  (select count(*) from public.test_attempt_responses response
                    where response.attempt_id = attempt.id and response.is_correct = false) incorrect_count,
                  greatest(0,
                    (select count(*) from public.test_version_questions question
                      where question.test_version_id = attempt.test_version_id)
                    - (select count(*) from public.test_attempt_responses response
                      where response.attempt_id = attempt.id and response.normalized_answer <> '[]'::jsonb)
                  ) unanswered_count
                from public.test_attempts attempt
                join public.test_assignments assignment
                  on assignment.id = attempt.test_assignment_id
                 and attempt.attempt_origin = 'ASSIGNMENT'
                join public.test_versions version on version.id = attempt.test_version_id
                join public.student_profiles student on student.user_id = attempt.student_id
                join public.profiles profile on profile.id = student.user_id
                left join public.course_sessions session on session.id = assignment.session_id
                """;
    }

    private TestAttemptSummaryResponse mapSummary(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        return new TestAttemptSummaryResponse(
                rs.getObject("attempt_id", UUID.class), rs.getObject("assignment_id", UUID.class),
                rs.getObject("test_version_id", UUID.class), rs.getObject("course_id", UUID.class),
                rs.getObject("session_id", UUID.class), rs.getObject("session_no", Short.class),
                rs.getString("session_title"), rs.getObject("student_id", UUID.class),
                rs.getString("student_code"), rs.getString("student_name"), rs.getString("test_title"),
                rs.getString("version_label"), rs.getString("primary_skill"), rs.getShort("attempt_no"),
                rs.getString("status"), rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("submitted_at", OffsetDateTime.class), rs.getLong("duration_seconds"),
                rs.getBigDecimal("score"), rs.getBigDecimal("max_score"), rs.getInt("correct_count"),
                rs.getInt("incorrect_count"), rs.getInt("unanswered_count")
        );
    }

    private void assertCanRead(UUID courseId, UUID actor, boolean moderator) {
        if (!memberships.courseExists(courseId)) {
            throw new ResourceNotFoundException("Không tìm thấy khóa học");
        }
        if (!moderator && !memberships.isTeacherAssigned(courseId, actor)) {
            throw new BusinessRuleException("Bạn không được phân công cho khóa học này");
        }
    }
}
