package com.theieltsspells.notification;

import com.theieltsspells.notification.application.dto.StudentNotificationPreferencesResponse;
import com.theieltsspells.notification.application.dto.UpdateStudentNotificationPreferencesRequest;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/** Durable notices committed together with a Writing result. */
@Service
@RequiredArgsConstructor
public class WritingNotificationService {
    private final JdbcTemplate jdbc;

    @Transactional
    public void completed(UUID evaluationId, boolean teacher) {
        // Evaluation ownership is a projection here; notification delivery owns its own rows.
        jdbc.update("""
                insert into public.notifications(user_id, type, title, body, data)
                select attempt.student_id, ?, ?, ?,
                  jsonb_build_object('attemptId', attempt.id, 'evaluationId', evaluation.id)
                from public.writing_evaluations evaluation
                join public.test_attempt_responses response on response.id=evaluation.test_attempt_response_id
                join public.test_attempts attempt on attempt.id=response.attempt_id
                where evaluation.id=?
                  and exists (
                    select 1 from public.student_profiles student
                    left join public.student_notification_preferences preferences on preferences.student_id = student.user_id
                    where student.user_id = attempt.student_id
                      and coalesce(preferences.grading_completed_alert, true)
                  )
                  and not exists(select 1 from public.notifications notice
                    where notice.type=? and notice.data->>'evaluationId'=cast(evaluation.id as text))
                """, teacher ? "WRITING_TEACHER" : "WRITING_TIS",
                teacher ? "Giáo viên đã công bố bài chấm Writing" : "TIS đã chấm xong bài Writing",
                "Điểm và nhận xét của bạn đã sẵn sàng.",
                evaluationId, teacher ? "WRITING_TEACHER" : "WRITING_TIS");
    }

    @Transactional
    public void speakingCompleted(UUID evaluationId, boolean teacher) {
        jdbc.update("""
                insert into public.notifications(user_id, type, title, body, data)
                select evaluation.student_id, ?, ?, ?,
                  jsonb_build_object('attemptId', evaluation.attempt_id, 'evaluationId', evaluation.id, 'skill', 'SPEAKING')
                from public.speaking_evaluations evaluation
                left join public.student_notification_preferences preferences
                  on preferences.student_id = evaluation.student_id
                where evaluation.id=? and coalesce(preferences.grading_completed_alert, true)
                  and not exists(select 1 from public.notifications notice
                    where notice.type=? and notice.data->>'evaluationId'=cast(evaluation.id as text))
                """, teacher ? "SPEAKING_TEACHER" : "SPEAKING_TIS",
                teacher ? "Giáo viên đã công bố bài chấm Speaking" : "TIS đã chấm xong bài Speaking",
                "Điểm và nhận xét bài nói của bạn đã sẵn sàng.", evaluationId,
                teacher ? "SPEAKING_TEACHER" : "SPEAKING_TIS");
    }

    @Transactional
    public void assignmentPublished(UUID assignmentId, UUID courseId, UUID classActivityId,
                                    String title, String skill) {
        jdbc.update("""
                insert into public.notifications(user_id, type, title, body, data)
                select enrollment.student_id, 'ASSIGNMENT_PUBLISHED', ?, ?,
                  jsonb_build_object('assignmentId', cast(? as text), 'courseId', cast(? as text),
                    'classActivityId', cast(? as text), 'skill', ?)
                from public.enrollments enrollment
                left join public.student_notification_preferences preferences
                  on preferences.student_id = enrollment.student_id
                where enrollment.course_id = ? and enrollment.status = 'ACTIVE'
                  and coalesce(preferences.assignment_deadline_reminder, true)
                  and not exists (
                    select 1 from public.notifications notice
                    where notice.user_id = enrollment.student_id
                      and notice.type = 'ASSIGNMENT_PUBLISHED'
                      and notice.data->>'assignmentId' = cast(? as text)
                  )
                """, "Bài tập mới: " + title,
                "Giáo viên đã giao một bài tập mới trong khóa học của bạn.",
                assignmentId, courseId, classActivityId, skill, courseId, assignmentId);
    }

    @Transactional
    public void assignmentReviewed(UUID studentId, UUID assignmentId, UUID courseId,
                                   UUID classActivityId, UUID attemptId, String title,
                                   String skill, String reviewStatus, String feedback) {
        boolean revision = "REVISION_REQUESTED".equalsIgnoreCase(reviewStatus)
                || "REJECTED".equalsIgnoreCase(reviewStatus);
        if (!enabled(studentId, "ASSIGNMENT_REVIEWED")) return;
        jdbc.update("""
                insert into public.notifications(user_id, type, title, body, data)
                values (?, ?, ?, ?, jsonb_build_object(
                  'assignmentId', cast(? as text), 'courseId', cast(? as text),
                  'classActivityId', cast(? as text), 'attemptId', cast(? as text),
                  'skill', ?, 'reviewStatus', ?))
                """, studentId, revision ? "ASSIGNMENT_REVISION_REQUESTED" : "ASSIGNMENT_REVIEWED",
                revision ? "Assignment cần được sửa lại" : "Assignment đã được chấm",
                revision
                        ? "Giáo viên đã gửi feedback cho bài " + title + ". Hãy xem nhận xét và nộp lại bản sửa."
                        : "Giáo viên đã hoàn tất chấm bài " + title + ". Điểm và feedback đã sẵn sàng.",
                assignmentId, courseId, classActivityId, attemptId, skill, reviewStatus);
    }

    @Transactional
    public void assignmentExtensionGranted(UUID studentId, UUID assignmentId, UUID courseId,
                                           UUID classActivityId, java.time.OffsetDateTime extensionDueAt) {
        if (!enabled(studentId, "ASSIGNMENT_EXTENSION_GRANTED")) return;
        jdbc.update("""
                insert into public.notifications(user_id, type, title, body, data)
                values (?, 'ASSIGNMENT_EXTENSION_GRANTED', 'Đã được gia hạn assignment',
                  'Giáo viên đã cấp thêm thời gian nộp bài cho bạn.', jsonb_build_object(
                    'assignmentId', cast(? as text), 'courseId', cast(? as text),
                    'classActivityId', cast(? as text), 'extensionDueAt', cast(? as text)))
                """, studentId, assignmentId, courseId, classActivityId, extensionDueAt);
    }

    @Scheduled(cron = "${app.notifications.timely-cron:0 * * * * *}", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void createTimelyNotifications() {
        jdbc.update("""
                insert into public.notifications(user_id, type, title, body, data)
                select enrollment.student_id, 'SESSION_UPCOMING',
                  'Buổi học sắp bắt đầu',
                  coalesce(session.title, 'Buổi học số ' || session.session_no) || ' sẽ bắt đầu trong 30 phút.',
                  jsonb_build_object('sessionId', cast(session.id as text),
                    'courseId', cast(course.id as text), 'startsAt', session.starts_at)
                from public.course_sessions session
                join public.courses course on course.id = session.course_id
                join public.enrollments enrollment on enrollment.course_id = course.id
                left join public.student_notification_preferences preferences
                  on preferences.student_id = enrollment.student_id
                where enrollment.status = 'ACTIVE'
                  and session.status = 'SCHEDULED'
                  and session.starts_at > now()
                  and session.starts_at <= now() + interval '30 minutes'
                  and coalesce(preferences.upcoming_class_reminder, true)
                on conflict do nothing
                """);

        jdbc.update("""
                with candidates as (
                  select enrollment.student_id, assignment.id assignment_id,
                    activity.id class_activity_id, activity.course_id,
                    assignment.title, learning_activity.skill::text skill,
                    greatest(
                      assignment.due_at + make_interval(mins => coalesce(assignment.late_grace_minutes, 0)),
                      latest.extension_due_at,
                      student_extension.extension_due_at
                    ) effective_due_at,
                    latest.submission_status, latest.review_status
                  from public.course_activities activity
                  join public.assignments assignment on assignment.id = activity.assignment_id
                  join public.learning_activities learning_activity on learning_activity.id = activity.activity_id
                  join public.enrollments enrollment on enrollment.course_id = activity.course_id
                  left join public.student_notification_preferences preferences
                    on preferences.student_id = enrollment.student_id
                  left join public.assignment_student_extensions student_extension
                    on student_extension.assignment_id = assignment.id
                    and student_extension.student_id = enrollment.student_id
                  left join lateral (
                    select submission.status::text submission_status,
                      submission.extension_due_at, attempt.review_status::text review_status
                    from public.student_activity_attempts attempt
                    join public.submissions submission on submission.id = attempt.submission_id
                    where attempt.class_activity_id = activity.id
                      and attempt.student_id = enrollment.student_id
                    order by attempt.attempt_no desc
                    limit 1
                  ) latest on true
                  where enrollment.status = 'ACTIVE'
                    and activity.is_published = true
                    and assignment.status = 'PUBLISHED'
                    and assignment.due_at is not null
                    and (assignment.opens_at is null or assignment.opens_at <= now())
                    and coalesce(preferences.assignment_deadline_reminder, true)
                    and (latest.submission_status is null
                      or latest.submission_status = 'DRAFT'
                      or latest.review_status in ('REVISION_REQUESTED', 'REJECTED'))
                )
                insert into public.notifications(user_id, type, title, body, data)
                select student_id, 'ASSIGNMENT_DUE', 'Assignment sắp đến hạn',
                  'Bài ' || title || ' sẽ đến hạn trong 24 giờ.',
                  jsonb_build_object('assignmentId', cast(assignment_id as text),
                    'courseId', cast(course_id as text),
                    'classActivityId', cast(class_activity_id as text),
                    'skill', skill, 'effectiveDueAt', effective_due_at)
                from candidates
                where effective_due_at > now()
                  and effective_due_at <= now() + interval '24 hours'
                on conflict do nothing
                """);
    }

    @Scheduled(cron = "${app.notifications.weekly-cron:0 0 8 * * MON}", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void createWeeklyProgressDigest() {
        jdbc.update("""
                with recipients as (
                  select distinct enrollment.student_id
                  from public.enrollments enrollment
                  left join public.student_notification_preferences preferences
                    on preferences.student_id = enrollment.student_id
                  where enrollment.status = 'ACTIVE'
                    and coalesce(preferences.weekly_progress_digest, true)
                ), stats as (
                  select recipient.student_id,
                    (select count(*) from public.submissions submission
                      where submission.student_id = recipient.student_id
                        and submission.submitted_at >= now() - interval '7 days') submissions,
                    (select count(*) from public.teacher_activity_reviews review
                      join public.student_activity_attempts attempt on attempt.id = review.attempt_id
                      where attempt.student_id = recipient.student_id
                        and review.reviewed_at >= now() - interval '7 days') feedbacks,
                    (select count(*) from public.student_activity_attempts attempt
                      where attempt.student_id = recipient.student_id
                        and attempt.reflection is not null
                        and attempt.updated_at >= now() - interval '7 days') reflections
                  from recipients recipient
                )
                insert into public.notifications(user_id, type, title, body, data)
                select student_id, 'WEEKLY_PROGRESS_DIGEST', 'Tổng kết học tập tuần qua',
                  format('7 ngày qua: %s bài đã nộp, %s feedback mới và %s reflection.',
                    submissions, feedbacks, reflections),
                  jsonb_build_object('weekStart',
                    to_char(date_trunc('week', now() at time zone 'Asia/Ho_Chi_Minh'), 'YYYY-MM-DD'))
                from stats
                on conflict do nothing
                """);
    }

    @Transactional(readOnly = true)
    public List<Notice> unread(UUID studentId) {
        return list(studentId, true);
    }

    @Transactional(readOnly = true)
    public List<Notice> list(UUID studentId, boolean unreadOnly) {
        String filter = unreadOnly ? " and read_at is null" : "";
        return jdbc.query("""
                select id, type, title, body, data->>'attemptId' attempt_id,
                  data->>'assignmentId' assignment_id, data->>'courseId' course_id,
                  data->>'classActivityId' class_activity_id,
                  coalesce(data->>'skill', case when type like 'SPEAKING%' then 'SPEAKING' else 'WRITING' end) skill,
                  created_at, read_at
                from public.notifications
                left join public.student_notification_preferences preferences on preferences.student_id = ?
                where user_id=?
                """ + filter + """
                  and type in ('WRITING_TIS', 'WRITING_TEACHER', 'SPEAKING_TIS', 'SPEAKING_TEACHER',
                    'SESSION_UPCOMING', 'ASSIGNMENT_DUE', 'ASSIGNMENT_PUBLISHED',
                    'ASSIGNMENT_REVIEWED', 'ASSIGNMENT_REVISION_REQUESTED',
                    'ASSIGNMENT_REMINDER', 'ASSIGNMENT_EXTENSION_GRANTED', 'WEEKLY_PROGRESS_DIGEST')
                  and case
                    when type in ('WRITING_TIS', 'WRITING_TEACHER', 'SPEAKING_TIS', 'SPEAKING_TEACHER',
                                  'ASSIGNMENT_REVIEWED', 'ASSIGNMENT_REVISION_REQUESTED')
                      then coalesce(preferences.grading_completed_alert, true)
                    when type in ('SESSION_UPCOMING') then coalesce(preferences.upcoming_class_reminder, true)
                    when type = 'WEEKLY_PROGRESS_DIGEST' then coalesce(preferences.weekly_progress_digest, true)
                    when type in ('ASSIGNMENT_DUE', 'ASSIGNMENT_PUBLISHED', 'ASSIGNMENT_REMINDER',
                                  'ASSIGNMENT_EXTENSION_GRANTED')
                      then coalesce(preferences.assignment_deadline_reminder, true)
                    else true
                  end
                order by created_at desc limit 30
                """, (rs, row) -> {
            String attemptId = rs.getString("attempt_id");
            String assignmentId = rs.getString("assignment_id");
            String courseId = rs.getString("course_id");
            String classActivityId = rs.getString("class_activity_id");
            String skill = rs.getString("skill");
            String link = "SESSION_UPCOMING".equals(rs.getString("type")) && courseId != null
                    ? "/student/courses/" + courseId + "?tab=schedule"
                    : courseId != null && classActivityId != null
                    ? "/student/courses/" + courseId + "?tab=assignments&activityId=" + classActivityId
                    : attemptId != null
                    ? ("/student/" + ("SPEAKING".equalsIgnoreCase(skill) ? "speaking" : "writing") + "/attempts/" + attemptId + "/result")
                    : "/student";
            return new Notice(
                    rs.getObject("id", UUID.class),
                    rs.getString("type"),
                    rs.getString("title"),
                    rs.getString("body"),
                    attemptId,
                    skill,
                    rs.getObject("created_at", java.time.OffsetDateTime.class),
                    rs.getObject("read_at", java.time.OffsetDateTime.class),
                    link, assignmentId, courseId, classActivityId
            );
        }, studentId, studentId);
    }

    @Transactional(readOnly = true)
    public StudentNotificationPreferencesResponse preferences(UUID studentId) {
        return jdbc.query("""
                select coalesce(preferences.upcoming_class_reminder, true) upcoming_class_reminder,
                  coalesce(preferences.assignment_deadline_reminder, true) assignment_deadline_reminder,
                  coalesce(preferences.grading_completed_alert, true) grading_completed_alert,
                  coalesce(preferences.weekly_progress_digest, true) weekly_progress_digest,
                  coalesce(preferences.browser_sound_alert, false) browser_sound_alert
                from public.student_profiles student
                left join public.student_notification_preferences preferences
                  on preferences.student_id = student.user_id
                where student.user_id = ?
                """, (rs, row) -> new StudentNotificationPreferencesResponse(
                rs.getBoolean("upcoming_class_reminder"),
                rs.getBoolean("assignment_deadline_reminder"),
                rs.getBoolean("grading_completed_alert"),
                rs.getBoolean("weekly_progress_digest"),
                rs.getBoolean("browser_sound_alert")
        ), studentId).stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học viên"));
    }

    @Transactional
    public StudentNotificationPreferencesResponse updatePreferences(UUID studentId,
                                                                      UpdateStudentNotificationPreferencesRequest request) {
        preferences(studentId);
        jdbc.update("""
                insert into public.student_notification_preferences(
                  student_id, upcoming_class_reminder, assignment_deadline_reminder,
                  grading_completed_alert, weekly_progress_digest, browser_sound_alert, updated_at
                ) values (?, ?, ?, ?, ?, ?, now())
                on conflict (student_id) do update set
                  upcoming_class_reminder = excluded.upcoming_class_reminder,
                  assignment_deadline_reminder = excluded.assignment_deadline_reminder,
                  grading_completed_alert = excluded.grading_completed_alert,
                  weekly_progress_digest = excluded.weekly_progress_digest,
                  browser_sound_alert = excluded.browser_sound_alert,
                  updated_at = now()
                """, studentId,
                valueOrDefault(request.upcomingClassReminder(), true),
                valueOrDefault(request.assignmentDeadlineReminder(), true),
                valueOrDefault(request.gradingCompletedAlert(), true),
                valueOrDefault(request.weeklyProgressDigest(), true),
                valueOrDefault(request.browserSoundAlert(), false));
        return preferences(studentId);
    }

    public boolean enabled(UUID studentId, String type) {
        return switch (type) {
            case "SESSION_UPCOMING" -> preferences(studentId).upcomingClassReminder();
            case "WEEKLY_PROGRESS_DIGEST" -> preferences(studentId).weeklyProgressDigest();
            case "ASSIGNMENT_REVIEWED", "ASSIGNMENT_REVISION_REQUESTED",
                 "WRITING_TIS", "WRITING_TEACHER", "SPEAKING_TIS", "SPEAKING_TEACHER" ->
                    preferences(studentId).gradingCompletedAlert();
            default -> preferences(studentId).assignmentDeadlineReminder();
        };
    }

    private boolean valueOrDefault(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    @Transactional
    public void markRead(UUID id, UUID studentId) {
        jdbc.update("update public.notifications set read_at=now() where id=? and user_id=? and read_at is null", id, studentId);
    }

    @Transactional
    public void markAllRead(UUID studentId) {
        jdbc.update("update public.notifications set read_at=now() where user_id=? and read_at is null", studentId);
    }

    public record Notice(
            UUID id,
            String type,
            String title,
            String body,
            String attemptId,
            String skill,
            java.time.OffsetDateTime createdAt,
            java.time.OffsetDateTime readAt,
            String link,
            String assignmentId,
            String courseId,
            String classActivityId
    ) {
        public Notice(UUID id, String title, String body, String attemptId, String skill) {
            this(id, "WRITING_TIS", title, body, attemptId, skill, java.time.OffsetDateTime.now(), null,
                    attemptId != null ? ("/student/" + ("SPEAKING".equalsIgnoreCase(skill) ? "speaking" : "writing") + "/attempts/" + attemptId + "/result") : "/student",
                    null, null, null);
        }
    }
}
