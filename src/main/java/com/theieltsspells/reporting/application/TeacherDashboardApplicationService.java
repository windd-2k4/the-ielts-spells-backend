package com.theieltsspells.reporting.application;

import com.theieltsspells.reporting.application.dto.TeacherDashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherDashboardApplicationService {

    private final JdbcTemplate jdbc;

    public TeacherDashboardResponse overview(UUID teacherId) {
        String teacherName = jdbc.query(
                "select full_name from public.profiles where id = ?",
                (rs, row) -> rs.getString("full_name"), teacherId)
                .stream().findFirst().orElse("Giáo viên");

        var courses = jdbc.query("""
                select course.id course_id, course.code course_code, course.name course_name,
                  course.skill_pair::text skill_pair, course.status::text status,
                  (select count(*) from public.enrollments enrollment
                   where enrollment.course_id = course.id and enrollment.status = 'ACTIVE') active_students,
                  (select min(session.starts_at) from public.course_sessions session
                   where session.course_id = course.id and session.starts_at >= now()
                     and session.status <> 'CANCELLED'::public.session_status) next_session_at,
                  (
                    (select count(*) from public.student_activity_attempts attempt
                     join public.course_activities activity on activity.id = attempt.class_activity_id
                     join public.submissions submission on submission.id = attempt.submission_id
                     where activity.course_id = course.id and submission.status <> 'DRAFT'::public.submission_status
                       and attempt.review_status = 'PENDING'::public.review_status
                       and not exists (select 1 from public.student_activity_attempts newer
                         where newer.class_activity_id = attempt.class_activity_id
                           and newer.student_id = attempt.student_id
                           and newer.attempt_no > attempt.attempt_no))
                    +
                    (select count(*) from public.writing_evaluations evaluation
                     join public.test_attempt_responses response on response.id=evaluation.test_attempt_response_id
                     join public.test_attempts attempt on attempt.id=response.attempt_id
                     join public.test_assignments test_assignment on test_assignment.id=attempt.test_assignment_id
                     where test_assignment.course_id=course.id and evaluation.teacher_status='AVAILABLE'
                       and not exists (select 1 from public.test_attempts newer
                         where newer.test_assignment_id=attempt.test_assignment_id
                           and newer.student_id=attempt.student_id
                           and newer.attempt_no > attempt.attempt_no))
                    +
                    (select count(*) from public.speaking_evaluations evaluation
                     join public.test_attempts attempt on attempt.id=evaluation.attempt_id
                     join public.test_assignments test_assignment on test_assignment.id=attempt.test_assignment_id
                     where test_assignment.course_id=course.id
                       and evaluation.question_key='__ATTEMPT__'
                       and evaluation.teacher_status='AVAILABLE'
                       and not exists (select 1 from public.test_attempts newer
                         where newer.test_assignment_id=attempt.test_assignment_id
                           and newer.student_id=attempt.student_id
                           and newer.attempt_no > attempt.attempt_no))
                  ) pending_reviews,
                  (select count(*) from public.student_activity_attempts attempt
                   join public.course_activities activity on activity.id = attempt.class_activity_id
                   join public.submissions submission on submission.id = attempt.submission_id
                   where activity.course_id = course.id and submission.status <> 'DRAFT'::public.submission_status
                     and attempt.review_status in ('REVISION_REQUESTED'::public.review_status, 'REJECTED'::public.review_status)
                     and not exists (select 1 from public.student_activity_attempts newer
                       where newer.class_activity_id = attempt.class_activity_id
                         and newer.student_id = attempt.student_id
                         and newer.attempt_no > attempt.attempt_no)) revision_requests,
                  coalesce((select round(
                    count(*) filter (where session.status = 'COMPLETED'::public.session_status) * 100.0
                    / nullif(course.total_sessions, 0))::int
                    from public.course_sessions session where session.course_id = course.id), 0) completion_percent
                from public.courses course
                join public.course_teachers assignment on assignment.course_id = course.id
                where assignment.teacher_id = ? and course.is_active = true
                order by next_session_at nulls last, course.code
                """, (rs, row) -> new TeacherDashboardResponse.CourseSummary(
                rs.getObject("course_id", UUID.class),
                rs.getString("course_code"),
                rs.getString("course_name"),
                upper(rs.getString("skill_pair")),
                upper(rs.getString("status")),
                rs.getInt("active_students"),
                rs.getObject("next_session_at", OffsetDateTime.class),
                rs.getInt("pending_reviews"),
                rs.getInt("revision_requests"),
                rs.getInt("completion_percent")
        ), teacherId);

        var queue = jdbc.query("""
                with assignment_ranked as (
                  select submission.id item_id, course.id course_id, course.code course_code,
                    course.name course_name, activity.id class_activity_id, session.id session_id,
                    session.session_no::integer session_no,
                    coalesce(session.title, 'Session ' || session.session_no) session_title,
                    learning.title activity_title, learning.skill::text skill,
                    submission.student_id, profile.full_name student_name, submission.attempt_no,
                    submission.submitted_at, submission.is_late,
                    submission.status::text submission_status, attempt.review_status::text review_status,
                    row_number() over (
                      partition by activity.id, submission.student_id
                      order by submission.attempt_no desc
                    ) latest_attempt
                  from public.submissions submission
                  join public.student_activity_attempts attempt on attempt.submission_id = submission.id
                  join public.course_activities activity on activity.id = attempt.class_activity_id
                  join public.courses course on course.id = activity.course_id
                  left join public.course_sessions session on session.id = activity.session_id
                  join public.learning_activities learning on learning.id = activity.activity_id
                  join public.profiles profile on profile.id = submission.student_id
                  join public.course_teachers assignment on assignment.course_id = course.id
                  where assignment.teacher_id = ?
                ),
                queue_items as (
                  select 'ASSIGNMENT' item_type, item_id, course_id, course_code, course_name,
                    class_activity_id, session_id, session_no, session_title, activity_title, skill,
                    student_id, student_name, attempt_no, submitted_at, is_late,
                    submission_status, review_status,
                    '/courses/' || course_id || '?tab=progress&sessionId=' || session_id
                      || '&skill=' || lower(skill) || '&activityId=' || class_activity_id
                      || '&studentId=' || student_id course_route
                  from assignment_ranked
                  where latest_attempt = 1
                    and submission_status <> 'DRAFT'
                    and review_status in ('PENDING', 'REVISION_REQUESTED', 'REJECTED')

                  union all

                  select 'WRITING', evaluation.id, course.id, course.code, course.name,
                    null::uuid, null::uuid, null::integer, null::text, version.title, 'WRITING',
                    attempt.student_id, profile.full_name, attempt.attempt_no,
                    coalesce(attempt.submitted_at, evaluation.created_at), false,
                    evaluation.status::text, 'PENDING', '/writing-evaluations?tab=writing&evaluationId=' || evaluation.id
                  from public.writing_evaluations evaluation
                  join public.test_attempt_responses response on response.id=evaluation.test_attempt_response_id
                  join public.test_attempts attempt on attempt.id=response.attempt_id
                  join public.test_assignments test_assignment on test_assignment.id=attempt.test_assignment_id
                  join public.test_versions version on version.id=attempt.test_version_id
                  join public.courses course on course.id=test_assignment.course_id
                  join public.profiles profile on profile.id=attempt.student_id
                  join public.course_teachers teacher_scope on teacher_scope.course_id=course.id
                    and teacher_scope.teacher_id=?
                  where evaluation.teacher_status='AVAILABLE'
                    and not exists (
                      select 1 from public.test_attempts newer
                      where newer.test_assignment_id=attempt.test_assignment_id
                        and newer.student_id=attempt.student_id
                        and newer.attempt_no > attempt.attempt_no
                    )

                  union all

                  select 'SPEAKING', evaluation.id, course.id, course.code, course.name,
                    null::uuid, null::uuid, null::integer, null::text, version.title, 'SPEAKING',
                    attempt.student_id, profile.full_name, attempt.attempt_no,
                    coalesce(attempt.submitted_at, evaluation.created_at), false,
                    evaluation.status::text, 'PENDING', '/writing-evaluations?tab=speaking&evaluationId=' || evaluation.id
                  from public.speaking_evaluations evaluation
                  join public.test_attempts attempt on attempt.id=evaluation.attempt_id
                  join public.test_assignments test_assignment on test_assignment.id=attempt.test_assignment_id
                  join public.test_versions version on version.id=attempt.test_version_id
                  join public.courses course on course.id=test_assignment.course_id
                  join public.profiles profile on profile.id=attempt.student_id
                  join public.course_teachers teacher_scope on teacher_scope.course_id=course.id
                    and teacher_scope.teacher_id=?
                  where evaluation.question_key='__ATTEMPT__'
                    and evaluation.teacher_status='AVAILABLE'
                    and not exists (
                      select 1 from public.test_attempts newer
                      where newer.test_assignment_id=attempt.test_assignment_id
                        and newer.student_id=attempt.student_id
                        and newer.attempt_no > attempt.attempt_no
                    )
                )
                select item_type, item_id, course_id, course_code, course_name, class_activity_id, session_id,
                  session_no, session_title, activity_title, skill, student_id, student_name,
                  attempt_no, submitted_at, is_late, submission_status, review_status, course_route
                from queue_items
                order by case when review_status = 'PENDING' then 0 else 1 end,
                  submitted_at nulls last, course_code, student_name
                limit 100
                """, (rs, row) -> new TeacherDashboardResponse.EvaluationQueueItem(
                rs.getString("item_type"), rs.getObject("item_id", UUID.class),
                rs.getObject("course_id", UUID.class),
                rs.getString("course_code"),
                rs.getString("course_name"),
                rs.getObject("class_activity_id", UUID.class),
                rs.getObject("session_id", UUID.class),
                rs.getObject("session_no", Integer.class),
                rs.getString("session_title"),
                rs.getString("activity_title"),
                upper(rs.getString("skill")),
                rs.getObject("student_id", UUID.class),
                rs.getString("student_name"),
                rs.getShort("attempt_no"),
                rs.getObject("submitted_at", OffsetDateTime.class),
                rs.getBoolean("is_late"),
                upper(rs.getString("submission_status")),
                upper(rs.getString("review_status")),
                rs.getString("course_route")
        ), teacherId, teacherId, teacherId);

        int pendingCount = (int) courses.stream().mapToLong(TeacherDashboardResponse.CourseSummary::pendingReviews).sum();
        int revisionCount = (int) courses.stream().mapToLong(TeacherDashboardResponse.CourseSummary::revisionRequests).sum();
        return new TeacherDashboardResponse(
                teacherName, pendingCount, revisionCount, courses, queue, OffsetDateTime.now());
    }

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
