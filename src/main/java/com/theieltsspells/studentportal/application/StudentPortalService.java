package com.theieltsspells.studentportal.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.studentportal.application.dto.StudentCourseResponse;
import com.theieltsspells.studentportal.application.dto.StudentCourseSessionResponse;
import com.theieltsspells.studentportal.application.dto.StudentPortalOverviewResponse;
import com.theieltsspells.studentportal.application.dto.StudentTargetBandResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentPortalService {

    private static final ZoneId BUSINESS_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public StudentPortalOverviewResponse overview(UUID studentId) {
        try {
            String payload = jdbc.queryForObject(overviewSql(), String.class, studentId);
            return objectMapper.readValue(payload, StudentPortalOverviewResponse.class);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResourceNotFoundException("Không tìm thấy hồ sơ học viên đang hoạt động");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể đọc dữ liệu tổng quan học viên", exception);
        }
    }

    public List<StudentCourseResponse> courses(UUID studentId) {
        return jdbc.query("""
                select course.id, course.code, course.name, course.description, course.level,
                  course.skill_pair::text skill_pair, course.target_band, course.total_sessions,
                  course.tuition_amount, course.capacity, course.starts_on, course.ends_on,
                  course.status::text course_status,
                  case when exists (
                    select 1 from public.enrollments enrollment
                    where enrollment.course_id = course.id and enrollment.student_id = ?
                      and enrollment.status <> 'WITHDRAWN'
                  ) then course.default_zoom_url else null end default_zoom_url
                from public.courses course
                where course.is_public = true and course.is_active = true
                order by course.starts_on, course.code
                """, (rs, ignored) -> new StudentCourseResponse(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("level"),
                rs.getString("skill_pair"),
                rs.getBigDecimal("target_band"),
                rs.getObject("total_sessions", Short.class),
                rs.getBigDecimal("tuition_amount"),
                rs.getObject("capacity", Short.class),
                rs.getObject("starts_on", LocalDate.class),
                rs.getObject("ends_on", LocalDate.class),
                rs.getString("course_status"),
                rs.getString("default_zoom_url")
        ), studentId);
    }

    public List<StudentCourseSessionResponse> courseSessions(UUID studentId, UUID courseId) {
        Boolean enrolled = jdbc.queryForObject("""
                select exists (
                  select 1 from public.enrollments
                  where student_id = ? and course_id = ? and status <> 'WITHDRAWN'
                )
                """, Boolean.class, studentId, courseId);
        if (!Boolean.TRUE.equals(enrolled)) {
            throw new ResourceNotFoundException("Không tìm thấy khóa học trong danh sách ghi danh");
        }

        return jdbc.query("""
                select session.id, session.course_id, session.session_no, session.title,
                  session.starts_at, session.ends_at, session.status::text session_status,
                  session.phase_name, coalesce(session.zoom_url, course.default_zoom_url) zoom_url,
                  coalesce(session_teacher.full_name, primary_teacher.full_name) teacher_name
                from public.course_sessions session
                join public.courses course on course.id = session.course_id
                left join public.profiles session_teacher on session_teacher.id = session.teacher_id
                left join public.course_teachers primary_assignment
                  on primary_assignment.course_id = course.id and primary_assignment.is_primary = true
                left join public.profiles primary_teacher on primary_teacher.id = primary_assignment.teacher_id
                where session.course_id = ?
                order by session.session_no
                """, (rs, ignored) -> new StudentCourseSessionResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("course_id", UUID.class),
                rs.getObject("session_no", Short.class),
                rs.getString("title"),
                rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("ends_at", OffsetDateTime.class),
                rs.getString("zoom_url"),
                rs.getString("session_status"),
                rs.getString("phase_name"),
                rs.getString("teacher_name")
        ), courseId);
    }

    private String overviewSql() {
        return """
                with params as (
                  select cast(? as uuid) student_id
                ), portal_profile as (
                  select profile.id, student.student_code, profile.full_name, profile.email,
                    profile.phone, profile.avatar_path, student.current_band, student.target_band,
                    student.joined_at
                  from public.student_profiles student
                  join public.profiles profile on profile.id = student.user_id
                  join params on params.student_id = student.user_id
                  where profile.is_active = true
                ), enrollment_items as (
                  select enrollment.id enrollment_id, enrollment.status::text enrollment_status,
                    course.id course_id, course.code course_code, course.name course_name,
                    course.description, course.level, course.skill_pair::text skill_pair,
                    course.target_band, course.starts_on, course.ends_on,
                    enrollment.planned_exam_month, enrollment.actual_exam_date,
                    enrollment.exam_registration_status,
                    count(session.id) filter (where session.status = 'COMPLETED') completed_sessions,
                    course.total_sessions,
                    (select teacher_profile.full_name
                       from public.course_teachers teacher
                       join public.profiles teacher_profile on teacher_profile.id = teacher.teacher_id
                      where teacher.course_id = course.id and teacher.is_primary = true
                      limit 1) primary_teacher_name,
                    min(session.starts_at) filter (
                      where session.status = 'SCHEDULED' and session.starts_at >= now()
                    ) next_session_at
                  from public.enrollments enrollment
                  join params on params.student_id = enrollment.student_id
                  join public.courses course on course.id = enrollment.course_id
                  left join public.course_sessions session on session.course_id = course.id
                  where enrollment.status <> 'WITHDRAWN'
                  group by enrollment.id, enrollment.status, course.id,
                    enrollment.planned_exam_month, enrollment.actual_exam_date,
                    enrollment.exam_registration_status
                ), upcoming_session_items as (
                  select session.id session_id, course.id course_id, course.code course_code,
                    course.name course_name, session.session_no, session.title, session.phase_name,
                    session.starts_at, session.ends_at, session.status::text session_status,
                    coalesce(session_teacher.full_name, primary_teacher.full_name) teacher_name,
                    coalesce(session.zoom_url, course.default_zoom_url) zoom_url
                  from public.course_sessions session
                  join public.courses course on course.id = session.course_id
                  join public.enrollments enrollment on enrollment.course_id = course.id
                  join params on params.student_id = enrollment.student_id
                  left join public.profiles session_teacher on session_teacher.id = session.teacher_id
                  left join public.course_teachers primary_assignment
                    on primary_assignment.course_id = course.id and primary_assignment.is_primary = true
                  left join public.profiles primary_teacher on primary_teacher.id = primary_assignment.teacher_id
                  where enrollment.status = 'ACTIVE'
                    and session.status = 'SCHEDULED' and session.ends_at >= now()
                  order by session.starts_at
                  limit 8
                ), recent_attempt_items as (
                  select attempt.id attempt_id, assignment.id assignment_id, version.title,
                    version.primary_skill::text skill, attempt.status::text attempt_status,
                    attempt.started_at, attempt.submitted_at,
                    coalesce(attempt.final_score, attempt.auto_score) score,
                    coalesce(sum(question.max_score), 0) max_score,
                    count(response.id) filter (where response.is_correct = true) correct_count,
                    count(question.id) total_questions,
                    coalesce(attempt.submitted_at, attempt.last_saved_at, attempt.started_at) activity_at
                  from public.test_attempts attempt
                  join params on params.student_id = attempt.student_id
                  left join public.test_assignments assignment on assignment.id = attempt.test_assignment_id
                  join public.test_versions version on version.id = attempt.test_version_id
                  left join public.test_version_questions question on question.test_version_id = version.id
                  left join public.test_attempt_responses response
                    on response.attempt_id = attempt.id and response.question_key = question.question_key
                  group by attempt.id, assignment.id, version.title, version.primary_skill
                  order by activity_at desc
                  limit 8
                ), assignment_items as (
                  select assignment.id assignment_id, assignment.test_version_id, version.title,
                    version.version_label, assignment.course_id, course.name course_name,
                    assignment.mode, assignment.opens_at, assignment.closes_at,
                    assignment.max_attempts, assignment.duration_seconds,
                    (select count(*) from public.test_attempts attempt
                      join params on params.student_id = attempt.student_id
                      where attempt.test_assignment_id = assignment.id) attempts_used,
                    (select count(*) from public.test_attempts attempt
                      join params on params.student_id = attempt.student_id
                      where attempt.test_assignment_id = assignment.id
                        and attempt.status in ('SUBMITTED', 'GRADED', 'EXPIRED')) completed_attempts_used,
                    (select max(attempt.expires_at) from public.test_attempts attempt
                      join params on params.student_id = attempt.student_id
                      where attempt.test_assignment_id = assignment.id
                        and attempt.status = 'IN_PROGRESS') active_attempt_expires_at
                  from public.test_assignments assignment
                  join public.test_versions version on version.id = assignment.test_version_id
                  join public.courses course on course.id = assignment.course_id
                  join public.enrollments enrollment on enrollment.course_id = assignment.course_id
                  join params on params.student_id = enrollment.student_id
                  where enrollment.status = 'ACTIVE'
                    and version.primary_skill = 'READING'
                    and assignment.archived_at is null
                  order by coalesce(assignment.opens_at, assignment.created_at) desc
                ), activity_days as (
                  select distinct (coalesce(attempt.submitted_at, attempt.last_saved_at, attempt.started_at)
                    at time zone 'Asia/Ho_Chi_Minh')::date activity_date
                  from public.test_attempts attempt
                  join params on params.student_id = attempt.student_id
                ), activity_summary_items as (
                  select (attempt.submitted_at at time zone 'Asia/Ho_Chi_Minh')::date activity_date,
                    count(*) filter (where version.primary_skill = 'READING')::integer reading,
                    count(*) filter (where version.primary_skill = 'LISTENING')::integer listening,
                    count(*) filter (where version.primary_skill = 'WRITING')::integer writing,
                    count(*) filter (where version.primary_skill = 'SPEAKING')::integer speaking,
                    count(*)::integer total_attempts
                  from public.test_attempts attempt
                  join params on params.student_id = attempt.student_id
                  join public.test_versions version on version.id = attempt.test_version_id
                  where attempt.submitted_at is not null
                    and (attempt.submitted_at at time zone 'Asia/Ho_Chi_Minh')::date
                      >= (now() at time zone 'Asia/Ho_Chi_Minh')::date - 41
                  group by (attempt.submitted_at at time zone 'Asia/Ho_Chi_Minh')::date
                ), activity_ranked as (
                  select activity_date, (row_number() over (order by activity_date desc) - 1)::integer day_offset
                  from activity_days
                ), activity_anchor as (
                  select max(activity_date) last_date from activity_days
                ), activity_streak as (
                  select case
                    when anchor.last_date is null
                      or anchor.last_date < (now() at time zone 'Asia/Ho_Chi_Minh')::date - 1 then 0
                    else count(*) filter (
                      where ranked.activity_date = anchor.last_date - ranked.day_offset
                    )
                  end current_streak_days
                  from activity_anchor anchor
                  left join activity_ranked ranked on true
                  group by anchor.last_date
                ), recommendation_items as (
                  select course.id course_id, course.code, course.name, course.description, course.level,
                    course.skill_pair::text skill_pair, course.target_band, course.starts_on, course.ends_on,
                    case
                      when profile.target_band is null then 'Khóa học đang mở tuyển sinh'
                      when course.target_band is null then 'Phù hợp để bổ sung nền tảng cho mục tiêu của bạn'
                      when course.target_band <= profile.target_band
                        then 'Gần với mục tiêu IELTS ' || trim(trailing '.0' from profile.target_band::text)
                      else 'Bước tiếp theo sau khi củng cố nền tảng hiện tại'
                    end reason
                  from public.courses course
                  cross join portal_profile profile
                  where course.is_public = true and course.is_active = true
                    and course.status in ('PLANNED', 'OPEN')
                    and not exists (
                      select 1 from public.enrollments enrollment
                      join params on params.student_id = enrollment.student_id
                      where enrollment.course_id = course.id and enrollment.status <> 'WITHDRAWN'
                    )
                  order by case
                      when coalesce(profile.target_band, profile.current_band) is null
                        or course.target_band is null then 1 else 0 end,
                    abs(coalesce(course.target_band, coalesce(profile.target_band, profile.current_band))
                      - coalesce(profile.target_band, profile.current_band)),
                    course.starts_on
                  limit 6
                )
                select jsonb_build_object(
                  'profile', jsonb_build_object(
                    'id', profile.id,
                    'studentCode', profile.student_code,
                    'fullName', profile.full_name,
                    'email', profile.email,
                    'phone', profile.phone,
                    'avatarPath', profile.avatar_path,
                    'currentBand', profile.current_band,
                    'targetBand', profile.target_band,
                    'joinedAt', profile.joined_at
                  ),
                  'metrics', jsonb_build_object(
                    'assignedTests', (select count(*) from assignment_items),
                    'pendingTests', (select count(*) from assignment_items assignment
                      where (assignment.opens_at is null or assignment.opens_at <= now())
                        and (assignment.closes_at is null or assignment.closes_at > now())
                        and assignment.completed_attempts_used < assignment.max_attempts),
                    'completedAttempts', (select count(*) from public.test_attempts attempt
                      join params on params.student_id = attempt.student_id
                      where attempt.status in ('SUBMITTED', 'GRADED', 'EXPIRED')),
                    'currentStreakDays', (select current_streak_days from activity_streak),
                    'lastActivityAt', (select max(coalesce(attempt.submitted_at,
                      attempt.last_saved_at, attempt.started_at)) from public.test_attempts attempt
                      join params on params.student_id = attempt.student_id)
                  ),
                  'enrollments', coalesce((select jsonb_agg(jsonb_build_object(
                    'enrollmentId', item.enrollment_id,
                    'status', upper(item.enrollment_status),
                    'courseId', item.course_id,
                    'courseCode', item.course_code,
                    'courseName', item.course_name,
                    'description', item.description,
                    'level', item.level,
                    'skillPair', item.skill_pair,
                    'courseTargetBand', item.target_band,
                    'startsOn', item.starts_on,
                    'endsOn', item.ends_on,
                    'completedSessions', item.completed_sessions,
                    'totalSessions', item.total_sessions,
                    'primaryTeacherName', item.primary_teacher_name,
                    'nextSessionAt', item.next_session_at,
                    'plannedExamMonth', item.planned_exam_month,
                    'actualExamDate', item.actual_exam_date,
                    'examRegistrationStatus', item.exam_registration_status
                  ) order by case item.enrollment_status
                    when 'ACTIVE' then 0 when 'PENDING' then 1 when 'PAUSED' then 2 else 3 end,
                    item.starts_on desc) from enrollment_items item), '[]'::jsonb),
                  'upcomingSessions', coalesce((select jsonb_agg(jsonb_build_object(
                    'sessionId', item.session_id,
                    'courseId', item.course_id,
                    'courseCode', item.course_code,
                    'courseName', item.course_name,
                    'sessionNo', item.session_no,
                    'title', item.title,
                    'phaseName', item.phase_name,
                    'startsAt', item.starts_at,
                    'endsAt', item.ends_at,
                    'status', upper(item.session_status),
                    'teacherName', item.teacher_name,
                    'zoomUrl', item.zoom_url
                  ) order by item.starts_at) from upcoming_session_items item), '[]'::jsonb),
                  'recentAttempts', coalesce((select jsonb_agg(jsonb_build_object(
                    'attemptId', item.attempt_id,
                    'assignmentId', item.assignment_id,
                    'title', item.title,
                    'skill', item.skill,
                    'status', upper(item.attempt_status),
                    'startedAt', item.started_at,
                    'submittedAt', item.submitted_at,
                    'score', item.score,
                    'maxScore', item.max_score,
                    'correctCount', case when item.submitted_at is null then null else item.correct_count end,
                    'totalQuestions', item.total_questions
                  ) order by item.activity_at desc) from recent_attempt_items item), '[]'::jsonb),
                  'activityCalendar', coalesce((select jsonb_agg(jsonb_build_object(
                    'activityDate', item.activity_date,
                    'reading', item.reading,
                    'listening', item.listening,
                    'writing', item.writing,
                    'speaking', item.speaking,
                    'totalAttempts', item.total_attempts
                  ) order by item.activity_date) from activity_summary_items item), '[]'::jsonb),
                  'readingAssignments', coalesce((select jsonb_agg(jsonb_build_object(
                    'assignmentId', item.assignment_id,
                    'testVersionId', item.test_version_id,
                    'title', item.title,
                    'versionLabel', item.version_label,
                    'courseId', item.course_id,
                    'courseName', item.course_name,
                    'mode', item.mode,
                    'opensAt', item.opens_at,
                    'closesAt', item.closes_at,
                    'maxAttempts', item.max_attempts,
                    'attemptsUsed', item.attempts_used,
                    'durationSeconds', item.duration_seconds,
                    'activeAttemptExpiresAt', item.active_attempt_expires_at
                  ) order by coalesce(item.opens_at, '-infinity'::timestamptz) desc)
                    from assignment_items item), '[]'::jsonb),
                  'recommendedCourses', coalesce((select jsonb_agg(jsonb_build_object(
                    'courseId', item.course_id,
                    'code', item.code,
                    'name', item.name,
                    'description', item.description,
                    'level', item.level,
                    'skillPair', item.skill_pair,
                    'targetBand', item.target_band,
                    'startsOn', item.starts_on,
                    'endsOn', item.ends_on,
                    'reason', item.reason
                  )) from recommendation_items item), '[]'::jsonb),
                  'aiStatus', 'DEVELOPMENT'
                )::text
                from portal_profile profile
                """;
    }

    @Transactional
    public StudentTargetBandResponse updateTargetBand(UUID studentId, BigDecimal targetBand) {
        if (targetBand == null
                || targetBand.remainder(new BigDecimal("0.5")).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessRuleException("Band mục tiêu phải theo bước 0.5");
        }
        var currentBands = jdbc.query("""
                select current_band from public.student_profiles where user_id = ?
                """, (rs, ignored) -> rs.getBigDecimal("current_band"), studentId);
        if (currentBands.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy hồ sơ học viên");
        }
        var currentBand = currentBands.getFirst();
        if (currentBand != null && targetBand.compareTo(currentBand) < 0) {
            throw new BusinessRuleException("Band mục tiêu không được thấp hơn Band hiện tại");
        }
        jdbc.update("""
                update public.student_profiles set target_band = ? where user_id = ?
                """, targetBand, studentId);
        return new StudentTargetBandResponse(currentBand, targetBand);
    }

    private StudentPortalOverviewResponse.Profile profile(UUID studentId) {
        var values = jdbc.query("""
                select profile.id, student.student_code, profile.full_name, profile.email,
                  profile.phone, profile.avatar_path, student.current_band, student.target_band, student.joined_at
                from public.student_profiles student
                join public.profiles profile on profile.id = student.user_id
                where student.user_id = ? and profile.is_active = true
                """, (rs, ignored) -> new StudentPortalOverviewResponse.Profile(
                rs.getObject("id", UUID.class),
                rs.getString("student_code"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("avatar_path"),
                rs.getBigDecimal("current_band"),
                rs.getBigDecimal("target_band"),
                rs.getObject("joined_at", LocalDate.class)
        ), studentId);
        if (values.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy hồ sơ học viên đang hoạt động");
        }
        return values.getFirst();
    }

    private List<StudentPortalOverviewResponse.Enrollment> enrollments(UUID studentId) {
        return jdbc.query("""
                select enrollment.id enrollment_id, enrollment.status::text enrollment_status,
                  course.id course_id, course.code course_code, course.name course_name,
                  course.description, course.level, course.skill_pair::text skill_pair,
                  course.target_band, course.starts_on, course.ends_on,
                  enrollment.planned_exam_month, enrollment.actual_exam_date,
                  enrollment.exam_registration_status,
                  count(session.id) filter (where session.status = 'COMPLETED') completed_sessions,
                  course.total_sessions,
                  (select teacher_profile.full_name
                     from public.course_teachers teacher
                     join public.profiles teacher_profile on teacher_profile.id = teacher.teacher_id
                    where teacher.course_id = course.id and teacher.is_primary = true
                    limit 1) primary_teacher_name,
                  min(session.starts_at) filter (
                    where session.status = 'SCHEDULED' and session.starts_at >= now()
                  ) next_session_at
                from public.enrollments enrollment
                join public.courses course on course.id = enrollment.course_id
                left join public.course_sessions session on session.course_id = course.id
                where enrollment.student_id = ? and enrollment.status <> 'WITHDRAWN'
                group by enrollment.id, enrollment.status, course.id,
                  enrollment.planned_exam_month, enrollment.actual_exam_date,
                  enrollment.exam_registration_status
                order by case enrollment.status
                  when 'ACTIVE' then 0 when 'PENDING' then 1 when 'PAUSED' then 2 else 3 end,
                  course.starts_on desc
                """, (rs, ignored) -> new StudentPortalOverviewResponse.Enrollment(
                rs.getObject("enrollment_id", UUID.class),
                rs.getString("enrollment_status").toUpperCase(),
                rs.getObject("course_id", UUID.class),
                rs.getString("course_code"),
                rs.getString("course_name"),
                rs.getString("description"),
                rs.getString("level"),
                rs.getString("skill_pair"),
                rs.getBigDecimal("target_band"),
                rs.getObject("starts_on", LocalDate.class),
                rs.getObject("ends_on", LocalDate.class),
                rs.getInt("completed_sessions"),
                rs.getInt("total_sessions"),
                rs.getString("primary_teacher_name"),
                rs.getObject("next_session_at", OffsetDateTime.class),
                rs.getObject("planned_exam_month", LocalDate.class),
                rs.getObject("actual_exam_date", LocalDate.class),
                rs.getString("exam_registration_status")
        ), studentId);
    }

    private List<StudentPortalOverviewResponse.UpcomingSession> upcomingSessions(UUID studentId) {
        return jdbc.query("""
                select session.id session_id, course.id course_id, course.code course_code,
                  course.name course_name, session.session_no, session.title, session.phase_name,
                  session.starts_at, session.ends_at, session.status::text session_status,
                  coalesce(session_teacher.full_name, primary_teacher.full_name) teacher_name,
                  coalesce(session.zoom_url, course.default_zoom_url) zoom_url
                from public.course_sessions session
                join public.courses course on course.id = session.course_id
                join public.enrollments enrollment on enrollment.course_id = course.id
                  and enrollment.student_id = ? and enrollment.status = 'ACTIVE'
                left join public.profiles session_teacher on session_teacher.id = session.teacher_id
                left join public.course_teachers primary_assignment
                  on primary_assignment.course_id = course.id and primary_assignment.is_primary = true
                left join public.profiles primary_teacher on primary_teacher.id = primary_assignment.teacher_id
                where session.status = 'SCHEDULED' and session.ends_at >= now()
                order by session.starts_at
                limit 8
                """, (rs, ignored) -> new StudentPortalOverviewResponse.UpcomingSession(
                rs.getObject("session_id", UUID.class),
                rs.getObject("course_id", UUID.class),
                rs.getString("course_code"),
                rs.getString("course_name"),
                rs.getInt("session_no"),
                rs.getString("title"),
                rs.getString("phase_name"),
                rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("ends_at", OffsetDateTime.class),
                rs.getString("session_status").toUpperCase(),
                rs.getString("teacher_name"),
                rs.getString("zoom_url")
        ), studentId);
    }

    private List<StudentPortalOverviewResponse.RecentAttempt> recentAttempts(UUID studentId) {
        return jdbc.query("""
                select attempt.id attempt_id, assignment.id assignment_id, version.title,
                  version.primary_skill, attempt.status::text attempt_status,
                  attempt.started_at, attempt.submitted_at,
                  coalesce(attempt.final_score, attempt.auto_score) score,
                  coalesce(sum(question.max_score), 0) max_score,
                  count(response.id) filter (where response.is_correct = true) correct_count,
                  count(question.id) total_questions
                from public.test_attempts attempt
                left join public.test_assignments assignment on assignment.id = attempt.test_assignment_id
                join public.test_versions version on version.id = attempt.test_version_id
                left join public.test_version_questions question on question.test_version_id = version.id
                left join public.test_attempt_responses response
                  on response.attempt_id = attempt.id and response.question_key = question.question_key
                where attempt.student_id = ?
                group by attempt.id, assignment.id, version.title, version.primary_skill
                order by coalesce(attempt.submitted_at, attempt.last_saved_at, attempt.started_at) desc
                limit 8
                """, (rs, ignored) -> {
            var score = rs.getBigDecimal("score");
            var submittedAt = rs.getObject("submitted_at", OffsetDateTime.class);
            return new StudentPortalOverviewResponse.RecentAttempt(
                    rs.getObject("attempt_id", UUID.class),
                    rs.getObject("assignment_id", UUID.class),
                    rs.getString("title"),
                    rs.getString("primary_skill"),
                    rs.getString("attempt_status").toUpperCase(),
                    rs.getObject("started_at", OffsetDateTime.class),
                    submittedAt,
                    score,
                    rs.getBigDecimal("max_score"),
                    submittedAt == null ? null : rs.getInt("correct_count"),
                    rs.getInt("total_questions")
            );
        }, studentId);
    }

    private StudentPortalOverviewResponse.Metrics metrics(UUID studentId) {
        var row = jdbc.query("""
                select
                  (select count(*)
                     from public.test_assignments assignment
                     join public.test_versions version on version.id = assignment.test_version_id
                    where assignment.archived_at is null and version.primary_skill = 'READING'
                      and exists (select 1 from public.enrollments enrollment
                                   where enrollment.course_id = assignment.course_id
                                     and enrollment.student_id = ? and enrollment.status = 'ACTIVE')) assigned_tests,
                  (select count(*) from public.test_attempts attempt
                    where attempt.student_id = ? and attempt.status in ('SUBMITTED', 'GRADED', 'EXPIRED')) completed_attempts,
                  (select max(coalesce(attempt.submitted_at, attempt.last_saved_at, attempt.started_at))
                     from public.test_attempts attempt where attempt.student_id = ?) last_activity_at
                """, (rs, ignored) -> new MetricsRow(
                rs.getInt("assigned_tests"),
                rs.getInt("completed_attempts"),
                rs.getObject("last_activity_at", OffsetDateTime.class)
        ), studentId, studentId, studentId).getFirst();
        int pending = jdbc.queryForObject("""
                select count(*)
                from public.test_assignments assignment
                join public.test_versions version on version.id = assignment.test_version_id
                where assignment.archived_at is null and version.primary_skill = 'READING'
                  and (assignment.opens_at is null or assignment.opens_at <= now())
                  and (assignment.closes_at is null or assignment.closes_at > now())
                  and exists (select 1 from public.enrollments enrollment
                               where enrollment.course_id = assignment.course_id
                                 and enrollment.student_id = ? and enrollment.status = 'ACTIVE')
                  and (select count(*) from public.test_attempts attempt
                        where attempt.test_assignment_id = assignment.id and attempt.student_id = ?
                          and attempt.status in ('SUBMITTED', 'GRADED', 'EXPIRED')) < assignment.max_attempts
                """, Integer.class, studentId, studentId);
        var activityDates = jdbc.query("""
                select distinct (coalesce(submitted_at, last_saved_at, started_at)
                  at time zone 'Asia/Ho_Chi_Minh')::date activity_date
                from public.test_attempts
                where student_id = ?
                order by activity_date desc
                limit 366
                """, (rs, ignored) -> rs.getObject("activity_date", LocalDate.class), studentId);
        return new StudentPortalOverviewResponse.Metrics(
                row.assignedTests(),
                pending,
                row.completedAttempts(),
                streak(activityDates),
                row.lastActivityAt()
        );
    }

    private int streak(List<LocalDate> activityDates) {
        if (activityDates.isEmpty()) {
            return 0;
        }
        var today = LocalDate.now(BUSINESS_TIME_ZONE);
        var cursor = activityDates.getFirst();
        if (cursor.isBefore(today.minusDays(1))) {
            return 0;
        }
        int streak = 1;
        for (int index = 1; index < activityDates.size(); index++) {
            if (!activityDates.get(index).equals(cursor.minusDays(1))) {
                break;
            }
            cursor = activityDates.get(index);
            streak++;
        }
        return streak;
    }

    private List<StudentPortalOverviewResponse.CourseRecommendation> recommendedCourses(
            UUID studentId,
            BigDecimal currentBand,
            BigDecimal targetBand
    ) {
        var referenceBand = targetBand != null ? targetBand : currentBand;
        return jdbc.query("""
                select course.id, course.code, course.name, course.description, course.level,
                  course.skill_pair::text skill_pair, course.target_band, course.starts_on, course.ends_on
                from public.courses course
                where course.is_public = true and course.is_active = true
                  and course.status in ('PLANNED', 'OPEN')
                  and not exists (select 1 from public.enrollments enrollment
                                  where enrollment.course_id = course.id and enrollment.student_id = ?
                                    and enrollment.status <> 'WITHDRAWN')
                order by case when ?::numeric is null or course.target_band is null then 1 else 0 end,
                  abs(coalesce(course.target_band, ?::numeric) - ?::numeric), course.starts_on
                limit 6
                """, (rs, ignored) -> {
            var courseTarget = rs.getBigDecimal("target_band");
            String reason;
            if (targetBand == null) {
                reason = "Khóa học đang mở tuyển sinh";
            } else if (courseTarget == null) {
                reason = "Phù hợp để bổ sung nền tảng cho mục tiêu của bạn";
            } else if (courseTarget.compareTo(targetBand) <= 0) {
                reason = "Gần với mục tiêu IELTS " + targetBand.stripTrailingZeros().toPlainString();
            } else {
                reason = "Bước tiếp theo sau khi củng cố nền tảng hiện tại";
            }
            return new StudentPortalOverviewResponse.CourseRecommendation(
                    rs.getObject("id", UUID.class),
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("level"),
                    rs.getString("skill_pair"),
                    courseTarget,
                    rs.getObject("starts_on", LocalDate.class),
                    rs.getObject("ends_on", LocalDate.class),
                    reason
            );
        }, studentId, referenceBand, referenceBand, referenceBand);
    }

    private record MetricsRow(int assignedTests, int completedAttempts, OffsetDateTime lastActivityAt) {
    }
}
