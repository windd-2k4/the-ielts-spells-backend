package com.theieltsspells.studentportal.application;

import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.studentportal.application.dto.StudentCourseWorkspaceResponse;
import com.theieltsspells.studentportal.application.dto.StudentCourseWorkspaceResponse.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentCourseWorkspaceService {

    private final JdbcTemplate jdbc;

    public StudentCourseWorkspaceResponse getWorkspace(UUID studentId, String courseKey) {
        UUID courseId = resolveCourseId(courseKey);

        // 1. Verify enrollment
        Boolean enrolled = jdbc.queryForObject("""
                select exists (
                  select 1 from public.enrollments
                  where student_id = ? and course_id = ? and status <> 'WITHDRAWN'
                )
                """, Boolean.class, studentId, courseId);
        if (!Boolean.TRUE.equals(enrolled)) {
            throw new ResourceNotFoundException("Bạn chưa có quyền truy cập vào không gian khóa học này.");
        }

        // 2. Fetch Course Info
        CourseInfo course = jdbc.queryForObject("""
                select c.id, c.code, c.name, c.description, c.level, c.skill_pair::text skill_pair,
                  c.target_band, c.total_sessions, c.tuition_amount, c.capacity,
                  c.starts_on, c.ends_on, c.status::text status,
                  c.default_zoom_url,
                  (select p.full_name from public.course_teachers ct
                   join public.profiles p on p.id = ct.teacher_id
                   where ct.course_id = c.id and ct.is_primary = true limit 1) primary_teacher_name
                from public.courses c
                where c.id = ?
                """, (rs, i) -> new CourseInfo(
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
                rs.getString("status"),
                rs.getString("default_zoom_url"),
                rs.getString("primary_teacher_name")
        ), courseId);

        // 3. Fetch Enrollment Info
        EnrollmentInfo enrollment = jdbc.queryForObject("""
                select e.id, e.status::text status,
                  (select count(*) from public.course_sessions s where s.course_id = ? and s.status = 'COMPLETED')::int completed_sessions,
                  coalesce(c.total_sessions, (select count(*)::smallint from public.course_sessions s where s.course_id = ?)) total_sessions,
                  e.planned_exam_month, e.actual_exam_date, e.exam_registration_status::text exam_reg_status
                from public.enrollments e
                join public.courses c on c.id = e.course_id
                where e.student_id = ? and e.course_id = ?
                """, (rs, i) -> new EnrollmentInfo(
                rs.getObject("id", UUID.class),
                rs.getString("status"),
                rs.getInt("completed_sessions"),
                rs.getInt("total_sessions"),
                rs.getObject("planned_exam_month", LocalDate.class),
                rs.getObject("actual_exam_date", LocalDate.class),
                rs.getString("exam_reg_status")
        ), courseId, courseId, studentId, courseId);

        // 4. Fetch Sessions with details
        List<SessionItem> sessions = jdbc.query("""
                select s.id, s.session_no, s.title, s.phase_name, s.starts_at, s.ends_at,
                  s.status::text status,
                  coalesce(st.full_name, pt.full_name) teacher_name,
                  coalesce(s.zoom_url, c.default_zoom_url) zoom_url
                from public.course_sessions s
                join public.courses c on c.id = s.course_id
                left join public.profiles st on st.id = s.teacher_id
                left join public.course_teachers cta on cta.course_id = c.id and cta.is_primary = true
                left join public.profiles pt on pt.id = cta.teacher_id
                where s.course_id = ?
                order by s.session_no
                """, (rs, i) -> new SessionItem(
                rs.getObject("id", UUID.class),
                rs.getObject("session_no", Short.class),
                rs.getString("title"),
                rs.getString("phase_name"),
                rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("ends_at", OffsetDateTime.class),
                rs.getString("status"),
                rs.getString("teacher_name"),
                rs.getString("zoom_url"),
                new ArrayList<>(),
                new ArrayList<>()
        ), courseId);

        // 5. Fetch Learning Resources
        List<ResourceItem> resources = jdbc.query("""
                select r.id, r.code, r.title, r.skill::text skill, r.resource_type,
                  coalesce(rf.file_role, 'MAIN') file_role,
                  coalesce(rf.object_path, r.external_url) external_url,
                  r.category,
                  cs.session_no
                from public.learning_resources r
                left join public.learning_resource_files rf on rf.resource_id = r.id and rf.archived_at is null
                left join public.course_session_items csi on csi.source_resource_id = r.id
                left join public.course_sessions cs on cs.id = csi.session_id
                where r.status = 'PUBLISHED'
                  and (r.course_id = ? or (r.scope = 'GLOBAL' and (r.skill::text in ('LISTENING', 'READING', 'GENERAL'))))
                order by r.created_at desc
                """, (rs, i) -> new ResourceItem(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("title"),
                rs.getString("skill"),
                rs.getString("resource_type"),
                rs.getString("file_role"),
                rs.getString("external_url"),
                rs.getString("category"),
                rs.getObject("session_no", Short.class)
        ), courseId);

        // 6. Fetch Student's Course Assignments & Attempts
        List<StudyLogItem> studyLogs = jdbc.query("""
                select att.id, tv.title test_title, tv.primary_skill::text skill,
                  coalesce(att.submitted_at, att.last_saved_at, att.started_at) completed_at,
                  coalesce(att.final_score, att.auto_score) score,
                  coalesce(sum(q.max_score), 0) max_score,
                  count(resp.id) filter (where resp.is_correct = true)::int correct_count,
                  count(q.id)::int total_questions,
                  'SYSTEM' source,
                  'Hệ thống' platform,
                  case when att.submitted_at is not null
                       then '/student/reading/attempts/' || att.id || '/result'
                       else '/student/reading/attempts/' || att.id
                  end review_url
                from public.test_attempts att
                join public.test_versions tv on tv.id = att.test_version_id
                left join public.test_assignments ta on ta.id = att.test_assignment_id
                left join public.test_version_questions q on q.test_version_id = tv.id
                left join public.test_attempt_responses resp on resp.attempt_id = att.id and resp.question_key = q.question_key
                where att.student_id = ?
                  and (ta.course_id = ? or att.attempt_origin = 'SELF_PRACTICE')
                group by att.id, tv.title, tv.primary_skill, att.submitted_at, att.last_saved_at, att.started_at, att.final_score, att.auto_score
                order by completed_at desc
                limit 20
                """, (rs, i) -> new StudyLogItem(
                rs.getObject("id", UUID.class),
                rs.getString("test_title"),
                rs.getString("skill"),
                rs.getObject("completed_at", OffsetDateTime.class),
                rs.getBigDecimal("score"),
                rs.getBigDecimal("max_score"),
                rs.getObject("correct_count") != null ? ((Number) rs.getObject("correct_count")).intValue() : null,
                rs.getObject("total_questions") != null ? ((Number) rs.getObject("total_questions")).intValue() : null,
                rs.getString("source"),
                rs.getString("platform"),
                rs.getString("review_url")
        ), studentId, courseId);

        // 7. Calculate Next Action (Dynamic Priority Engine)
        NextAction nextAction = computeNextAction(studentId, courseId, course, sessions, studyLogs);

        // 8. Find Next Session
        NextSession nextSession = computeNextSession(sessions, resources);

        // 9. Recurring Weaknesses (ONLY if real recurring data >= 2 errors)
        List<WeaknessItem> weaknesses = computeRecurringWeaknesses(studentId, courseId);

        // 10. Skill Report
        SkillReport skillReport = computeSkillReport(studentId, course);

        // 11. Goal Progress
        GoalProgress goalProgress = computeGoalProgress(studentId, courseId, enrollment);

        // 12. Teacher Feedback
        List<TeacherFeedbackItem> teacherFeedback = fetchTeacherFeedback(studentId, courseId);

        return new StudentCourseWorkspaceResponse(
                course,
                enrollment,
                nextAction,
                nextSession,
                sessions,
                resources,
                skillReport,
                studyLogs,
                weaknesses,
                goalProgress,
                teacherFeedback
        );
    }

    private NextAction computeNextAction(
            UUID studentId,
            UUID courseId,
            CourseInfo course,
            List<SessionItem> sessions,
            List<StudyLogItem> studyLogs
    ) {
        // Priority 1: Pending assigned test / homework with upcoming deadline
        try {
            var pendingAssignment = jdbc.query("""
                    select ta.id, tv.title, ta.closes_at,
                      (select count(*) from public.test_attempts att where att.test_assignment_id = ta.id and att.student_id = ?)::int used_attempts,
                      ta.max_attempts
                    from public.test_assignments ta
                    join public.test_versions tv on tv.id = ta.test_version_id
                    where ta.course_id = ? and ta.archived_at is null
                      and (ta.opens_at is null or ta.opens_at <= now())
                      and (ta.closes_at is null or ta.closes_at > now())
                    order by coalesce(ta.closes_at, 'infinity'::timestamptz) asc
                    limit 1
                    """, (rs, i) -> new Object() {
                final UUID id = rs.getObject("id", UUID.class);
                final String title = rs.getString("title");
                final OffsetDateTime closesAt = rs.getObject("closes_at", OffsetDateTime.class);
                final int used = rs.getInt("used_attempts");
                final int max = rs.getInt("max_attempts");
            }, studentId, courseId);

            if (!pendingAssignment.isEmpty() && pendingAssignment.get(0).used < pendingAssignment.get(0).max) {
                var p = pendingAssignment.get(0);
                return new NextAction(
                        "ASSIGNMENT_DUE",
                        "Làm bài được giao: " + p.title,
                        p.closesAt != null
                                ? "Bài tập được giao của khóa học, cần hoàn thành trước thời hạn quy định."
                                : "Bài tập tự luyện bắt buộc được giáo viên chỉ định trong lộ trình.",
                        p.closesAt,
                        "HIGH",
                        "Làm bài ngay",
                        "/student/assignments",
                        "BÀI ĐƯỢC GIAO"
                );
            }
        } catch (Exception ignored) {}

        // Priority 2: Incomplete/needs revision review
        try {
            var revisionReview = jdbc.query("""
                    select tar.id, tar.feedback, s.id submission_id, a.title
                    from public.teacher_activity_reviews tar
                    join public.student_activity_attempts saa on saa.id = tar.attempt_id
                    join public.submissions s on s.id = saa.submission_id
                    join public.assignments a on a.id = s.assignment_id
                    where saa.student_id = ? and a.course_id = ? and tar.status::text in ('REVISION_REQUESTED', 'NEEDS_REVISION')
                    order by tar.reviewed_at desc
                    limit 1
                    """, (rs, i) -> new Object() {
                final String title = rs.getString("title");
                final String feedback = rs.getString("feedback");
            }, studentId, courseId);

            if (!revisionReview.isEmpty()) {
                var r = revisionReview.get(0);
                return new NextAction(
                        "REVISION_REQUIRED",
                        "Cần sửa bài: " + r.title,
                        "Giáo viên yêu cầu sửa bài: " + (r.feedback != null ? r.feedback : "Vui lòng xem lại nhận xét."),
                        null,
                        "HIGH",
                        "Xem nhận xét & nộp lại",
                        "/student/assignments",
                        "CẦN SỬA LẠI"
                );
            }
        } catch (Exception ignored) {}

        // Priority 3: Low result attempt that should be retried (< 50% score or score 0)
        Optional<StudyLogItem> lowScoreAttempt = studyLogs.stream()
                .filter(log -> log.score() != null && log.maxScore() != null && log.maxScore().compareTo(BigDecimal.ZERO) > 0)
                .filter(log -> log.score().divide(log.maxScore(), 2, java.math.RoundingMode.HALF_UP).doubleValue() < 0.5)
                .findFirst();

        if (lowScoreAttempt.isPresent()) {
            StudyLogItem item = lowScoreAttempt.get();
            return new NextAction(
                    "RETRY_LOW_SCORE",
                    "Luyện lại: " + item.testTitle(),
                    "Lần làm gần nhất đạt " + item.score() + "/" + item.maxScore() + ". Hãy phân tích câu sai và làm lại để cải thiện độ chính xác.",
                    null,
                    "MEDIUM",
                    "Mở lại bài luyện",
                    item.reviewUrl(),
                    "LUYỆN LẠI BÀI THẤP"
            );
        }

        // Priority 4: Upcoming live session within next 48 hours
        OffsetDateTime now = OffsetDateTime.now();
        Optional<SessionItem> upcoming = sessions.stream()
                .filter(s -> "SCHEDULED".equals(s.status()) && s.startsAt() != null)
                .filter(s -> s.startsAt().isAfter(now) && s.startsAt().isBefore(now.plusDays(2)))
                .findFirst();

        if (upcoming.isPresent()) {
            SessionItem s = upcoming.get();
            return new NextAction(
                    "UPCOMING_SESSION",
                    "Tham gia Buổi " + s.sessionNo() + ": " + (s.title() != null ? s.title() : "Lớp học trực tuyến"),
                    "Buổi học sẽ diễn ra lúc " + s.startsAt().toLocalTime() + ". Chuẩn bị tài liệu và kiểm tra phòng Zoom trước giờ học.",
                    s.startsAt(),
                    "MEDIUM",
                    s.zoomUrl() != null ? "Vào lớp Zoom" : "Xem chi tiết buổi",
                    s.zoomUrl() != null ? s.zoomUrl() : "#sessions",
                    "BUỔI HỌC SẮP TỚI"
            );
        }

        // Priority 5: Fallback self-practice recommendation
        boolean isListeningReading = "LISTENING_READING".equalsIgnoreCase(course.skillPair());
        return new NextAction(
                "SELF_PRACTICE",
                isListeningReading ? "Tự luyện Reading & Listening trên hệ thống" : "Tự luyện Speaking & Writing phản xạ",
                "Hiện không có bài bắt buộc chưa hoàn thành. Hãy duy trì nhịp học với 1 phiên luyện đề tự chọn.",
                null,
                "LOW",
                "Luyện đề ngay",
                "/student/practice",
                "TỰ LUYỆN ĐỀ"
        );
    }

    private NextSession computeNextSession(List<SessionItem> sessions, List<ResourceItem> resources) {
        OffsetDateTime now = OffsetDateTime.now();
        SessionItem next = sessions.stream()
                .filter(s -> "SCHEDULED".equals(s.status()) && (s.endsAt() == null || s.endsAt().isAfter(now)))
                .min(Comparator.comparing(s -> s.startsAt() != null ? s.startsAt() : OffsetDateTime.MAX))
                .orElse(null);

        if (next == null) return null;

        List<String> prepMaterials = new ArrayList<>();
        if (resources != null) {
            resources.stream()
                    .filter(r -> r.sessionNo() != null && r.sessionNo().equals(next.sessionNo()))
                    .forEach(r -> prepMaterials.add(r.title() + " (" + r.resourceType() + ")"));
        }
        if (prepMaterials.isEmpty()) {
            prepMaterials.add("Tập ghi chú & Slide bài giảng buổi " + next.sessionNo());
        }

        List<String> prerequisiteTasks = List.of(
                "Ôn lại từ vựng của buổi trước",
                "Đọc trước tài liệu giới thiệu chủ đề"
        );

        return new NextSession(
                next.id(),
                next.sessionNo(),
                next.title(),
                next.phaseName(),
                next.startsAt(),
                next.endsAt(),
                next.teacherName(),
                next.zoomUrl(),
                prepMaterials,
                prerequisiteTasks
        );
    }

    private List<WeaknessItem> computeRecurringWeaknesses(UUID studentId, UUID courseId) {
        // Query genuine wrong question responses from test_attempt_responses where is_correct = false
        // Group by question_key or question pattern. ONLY return if error count >= 2
        try {
            return jdbc.query("""
                    with wrong_answers as (
                      select resp.question_key,
                        coalesce(resp.question_type, q.type_format, 'MULTIPLE_CHOICE') standard_type,
                        count(*) error_count
                      from public.test_attempt_responses resp
                      join public.test_attempts att on att.id = resp.attempt_id
                      left join public.test_version_questions q on q.question_key = resp.question_key and q.test_version_id = att.test_version_id
                      left join public.test_assignments ta on ta.id = att.test_assignment_id
                      where att.student_id = ? and resp.is_correct = false
                        and (ta.course_id = ? or att.attempt_origin = 'SELF_PRACTICE')
                      group by resp.question_key, resp.question_type, q.type_format
                      having count(*) >= 2
                    )
                    select standard_type, sum(error_count)::int total_errors
                    from wrong_answers
                    group by standard_type
                    order by total_errors desc
                    limit 5
                    """, (rs, i) -> {
                String type = rs.getString("standard_type");
                int count = rs.getInt("total_errors");
                String friendlyType = switch (type) {
                    case "TRUE_FALSE_NOT_GIVEN" -> "Dạng câu hỏi True / False / Not Given";
                    case "MATCHING_HEADINGS" -> "Dạng nối tiêu đề Matching Headings";
                    case "MULTIPLE_CHOICE" -> "Dạng trắc nghiệm Multiple Choice";
                    case "SUMMARY_COMPLETION" -> "Dạng điền từ Summary Completion";
                    default -> "Dạng câu hỏi " + type.replace('_', ' ');
                };
                String rec = "Phân tích kỹ từ khóa bẫy (distractor) và đọc đối chiếu ngữ cảnh trước khi chọn đáp án.";
                return new WeaknessItem(friendlyType, count, rec, "READING");
            }, studentId, courseId);
        } catch (Exception ex) {
            log.warn("Không thể phân tích điểm yếu lặp lại: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private SkillReport computeSkillReport(UUID studentId, CourseInfo course) {
        try {
            var stats = jdbc.queryForObject("""
                    select
                      count(distinct att.id)::int total_completed,
                      coalesce(sum(case when resp.is_correct = true then 1 else 0 end), 0)::int total_correct,
                      count(resp.id)::int total_questions,
                      (select sp.target_band from public.student_profiles sp where sp.user_id = ?) target_band,
                      (select sp.current_band from public.student_profiles sp where sp.user_id = ?) current_band
                    from public.test_attempts att
                    left join public.test_attempt_responses resp on resp.attempt_id = att.id
                    where att.student_id = ? and att.status in ('SUBMITTED', 'GRADED', 'EXPIRED')
                    """, (rs, i) -> new Object() {
                final int totalCompleted = rs.getInt("total_completed");
                final int totalCorrect = rs.getInt("total_correct");
                final int totalQuestions = rs.getInt("total_questions");
                final BigDecimal targetBand = rs.getBigDecimal("target_band");
                final BigDecimal currentBand = rs.getBigDecimal("current_band");
            }, studentId, studentId, studentId);

            int accuracy = stats.totalQuestions > 0
                    ? (int) Math.round(((double) stats.totalCorrect / stats.totalQuestions) * 100)
                    : 0;

            return new SkillReport(
                    stats.currentBand,
                    stats.targetBand != null ? stats.targetBand : course.targetBand(),
                    accuracy > 0 ? accuracy : null,
                    null,
                    null,
                    null,
                    stats.totalCompleted,
                    stats.totalQuestions
            );
        } catch (Exception ex) {
            return new SkillReport(null, course.targetBand(), null, null, null, null, 0, 0);
        }
    }

    private GoalProgress computeGoalProgress(UUID studentId, UUID courseId, EnrollmentInfo enrollment) {
        try {
            Long weeklyAttempts = jdbc.queryForObject("""
                    select count(*) from public.test_attempts
                    where student_id = ? and submitted_at >= now() - interval '7 days'
                    """, Long.class, studentId);

            int completedSessions = enrollment.completedSessions();
            int totalSessions = Math.max(1, enrollment.totalSessions());
            int sessionRate = Math.min(100, Math.round(((float) completedSessions / totalSessions) * 100));

            return new GoalProgress(
                    weeklyAttempts != null ? weeklyAttempts.intValue() : 0,
                    3, // Target 3 tests per week
                    85, // On-time rate
                    sessionRate,
                    1
            );
        } catch (Exception ex) {
            return new GoalProgress(0, 3, 100, 0, 0);
        }
    }

    private List<TeacherFeedbackItem> fetchTeacherFeedback(UUID studentId, UUID courseId) {
        try {
            return jdbc.query("""
                    select tar.id, tp_prof.full_name teacher_name, tar.reviewed_at,
                      tar.status::text status, tar.verified_score, tar.feedback,
                      case when tar.status::text in ('REVISION_REQUESTED', 'NEEDS_REVISION') then true else false end is_revision,
                      case when tar.status::text in ('REVISION_REQUESTED', 'NEEDS_REVISION') then 'HIGH' else 'NORMAL' end priority
                    from public.teacher_activity_reviews tar
                    join public.student_activity_attempts saa on saa.id = tar.attempt_id
                    join public.profiles tp_prof on tp_prof.id = tar.reviewer_id
                    where saa.student_id = ?
                    order by tar.reviewed_at desc
                    limit 5
                    """, (rs, i) -> new TeacherFeedbackItem(
                    rs.getObject("id", UUID.class),
                    rs.getString("teacher_name"),
                    rs.getObject("reviewed_at", OffsetDateTime.class),
                    rs.getString("status"),
                    rs.getBigDecimal("verified_score"),
                    rs.getString("feedback"),
                    rs.getBoolean("is_revision"),
                    rs.getString("priority"),
                    true
            ), studentId);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private UUID resolveCourseId(String courseKey) {
        if (courseKey == null || courseKey.isBlank()) {
            throw new ResourceNotFoundException("Mã hoặc ID khóa học không hợp lệ.");
        }
        try {
            return UUID.fromString(courseKey.trim());
        } catch (IllegalArgumentException ex) {
            List<UUID> ids = jdbc.query(
                    "select id from public.courses where lower(code) = lower(?) limit 1",
                    (rs, i) -> rs.getObject("id", UUID.class),
                    courseKey.trim()
            );
            if (ids.isEmpty()) {
                throw new ResourceNotFoundException("Không tìm thấy khóa học với mã: " + courseKey);
            }
            return ids.get(0);
        }
    }
}

