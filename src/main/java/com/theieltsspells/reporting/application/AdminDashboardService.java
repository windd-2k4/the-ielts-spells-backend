package com.theieltsspells.reporting.application;

import com.theieltsspells.reporting.application.dto.AdminDashboardResponse;
import com.theieltsspells.reporting.application.dto.AdminDashboardResponse.*;
import com.theieltsspells.shared.persistence.enums.ClassStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final JdbcTemplate jdbc;

    public AdminDashboardResponse overview() {
        return overview("7d", null);
    }

    public AdminDashboardResponse overview(String timeRange, UUID courseId) {
        int rangeDays = switch (timeRange != null ? timeRange.toLowerCase() : "7d") {
            case "today" -> 1;
            case "30d" -> 30;
            case "all" -> 36_500;
            default -> 7;
        };

        // 1. Backward Compatibility Legacy Metrics & Upcoming Courses
        var legacyData = fetchLegacyMetrics();

        // 2. Executive KPIs (5-6 key operational indicators with previous period comparisons)
        var kpis = computeExecutiveKpis(rangeDays, courseId, !"all".equalsIgnoreCase(timeRange));

        // 3. Action Items (Ranked by priority: HIGH -> MEDIUM -> LOW)
        var actionItems = computeActionItems(courseId);

        // 4. Learning Trend Points for interactive Line Chart
        var trend = computeLearningTrend(rangeDays, courseId);

        // 5. At-Risk Students
        var atRiskStudents = computeAtRiskStudents(courseId);

        // 6. Operational Schedule for next 7-14 days
        var scheduleEvents = computeScheduleEvents(courseId);

        // 7. Course Performance Table
        var coursePerformances = computeCoursePerformances(courseId);

        // 8. Available courses for dropdown filter
        var courseFilterOptions = fetchCourseFilterOptions();

        return new AdminDashboardResponse(
                legacyData.activeEnrollments(),
                legacyData.openCourses(),
                legacyData.activeCourses(),
                legacyData.totalCourses(),
                legacyData.upcomingCourses(),
                kpis,
                actionItems,
                trend,
                atRiskStudents,
                scheduleEvents,
                coursePerformances,
                courseFilterOptions,
                OffsetDateTime.now()
        );
    }

    private LegacyData fetchLegacyMetrics() {
        var rows = jdbc.query("""
                with normalized_courses as (
                  select course.*,
                    case
                      when course.status = 'PLANNED' then 'OPEN'::public.class_status
                      when course.status = 'OPEN' and course.starts_on <= current_date
                        then 'ACTIVE'::public.class_status
                      else course.status
                    end effective_status
                  from public.courses course
                ), metrics as (
                  select
                    (select count(*) from public.enrollments where status = 'ACTIVE') active_enrollments,
                    count(*) filter (
                      where is_active = true and effective_status in ('OPEN', 'ACTIVE')
                    ) open_courses,
                    count(*) filter (where is_active = true) active_courses,
                    count(*) total_courses
                  from normalized_courses
                ), upcoming as (
                  select id, code, name, capacity, starts_on, effective_status
                  from normalized_courses
                  where is_active = true
                    and effective_status not in ('COMPLETED', 'CANCELLED')
                    and starts_on >= current_date
                  order by starts_on, code
                  limit 4
                )
                select metrics.active_enrollments, metrics.open_courses,
                  metrics.active_courses, metrics.total_courses,
                  upcoming.id, upcoming.code, upcoming.name, upcoming.capacity,
                  upcoming.starts_on, upcoming.effective_status
                from metrics
                left join upcoming on true
                order by upcoming.starts_on, upcoming.code
                """, (rs, ignored) -> new DashboardRow(
                rs.getLong("active_enrollments"),
                rs.getLong("open_courses"),
                rs.getLong("active_courses"),
                rs.getLong("total_courses"),
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getObject("capacity", Short.class),
                rs.getObject("starts_on", LocalDate.class),
                rs.getString("effective_status")
        ));

        if (rows.isEmpty()) {
            return new LegacyData(0, 0, 0, 0, Collections.emptyList());
        }

        var first = rows.getFirst();
        var upcoming = new ArrayList<UpcomingCourse>();
        for (var row : rows) {
            if (row.id() == null) continue;
            try {
                upcoming.add(new UpcomingCourse(
                        row.id(), row.code(), row.name(), row.capacity() != null ? row.capacity() : 0,
                        row.startsOn(),
                        ClassStatus.valueOf(row.status())
                ));
            } catch (Exception ex) {
                // Ignore enum mismatch
            }
        }
        return new LegacyData(
                first.activeEnrollments(), first.openCourses(), first.activeCourses(), first.totalCourses(), upcoming
        );
    }

    private ExecutiveKpis computeExecutiveKpis(int rangeDays, UUID courseId, boolean comparePrevious) {
        double activeStudentsCount = count("""
                select count(distinct student_id)
                from public.enrollments
                where status = 'ACTIVE'
                  and (cast(? as uuid) is null or course_id = cast(? as uuid))
                """, courseId, courseId);
        Double previousActiveStudents = null;
        Double activeChangePercent = null;
        KpiMetric activeStudents = new KpiMetric(
                activeStudentsCount,
                String.format("%.0f", activeStudentsCount),
                previousActiveStudents,
                activeChangePercent,
                changeType(activeChangePercent),
                "Số học viên có lượt ghi danh đang hoạt động",
                "/students?tab=students"
        );

        double pendingLeadsCount = count("""
                select count(*)
                from public.leads
                where status in ('NEW', 'CONTACTED')
                  and (cast(? as uuid) is null or interested_course_id = cast(? as uuid))
                """, courseId, courseId);
        Double previousPendingLeads = null;
        Double leadsChangePercent = null;
        KpiMetric pendingLeads = new KpiMetric(
                pendingLeadsCount,
                String.format("%.0f", pendingLeadsCount),
                previousPendingLeads,
                leadsChangePercent,
                changeType(leadsChangePercent),
                "Lead mới hoặc đang được tư vấn nhưng chưa chuyển đổi",
                "/students?tab=leads"
        );

        Ratio conversion = leadConversion(rangeDays, 0, courseId);
        Ratio previousConversion = comparePrevious ? leadConversion(rangeDays, rangeDays, courseId) : null;
        double conversionRate = conversion.percentage();
        Double previousConversionRate = previousConversion == null || previousConversion.denominator() == 0
                ? null : previousConversion.percentage();
        Double conversionChange = previousConversionRate == null
                ? null : Math.round((conversionRate - previousConversionRate) * 10.0) / 10.0;
        KpiMetric leadConversion = new KpiMetric(
                conversionRate,
                conversion.denominator() == 0 ? "—" : String.format("%.0f%%", conversionRate),
                previousConversionRate,
                conversionChange,
                changeType(conversionChange),
                "Tỷ lệ lead tạo trong kỳ đã chuyển thành học viên (" + conversion.numerator() + "/" + conversion.denominator() + ")",
                "/students?tab=leads"
        );

        Ratio attendance = attendanceRatio(rangeDays, 0, courseId);
        Ratio previousAttendance = comparePrevious ? attendanceRatio(rangeDays, rangeDays, courseId) : null;
        double attendanceRate = attendance.percentage();
        Double previousAttendanceRate = previousAttendance == null || previousAttendance.denominator() == 0
                ? null : previousAttendance.percentage();
        Double attendanceChange = previousAttendanceRate == null || attendance.denominator() == 0
                ? null : Math.round((attendanceRate - previousAttendanceRate) * 10.0) / 10.0;
        KpiMetric averageAttendance = new KpiMetric(
                attendanceRate,
                attendance.denominator() == 0 ? "—" : String.format("%.0f%%", attendanceRate),
                previousAttendanceRate,
                attendanceChange,
                changeType(attendanceChange),
                attendance.denominator() == 0
                        ? "Chưa có bản ghi điểm danh trong kỳ"
                        : "Tỷ lệ hiện diện trên các lượt điểm danh đã xác nhận",
                courseId == null ? "/courses" : "/courses/" + courseId + "?tab=attendance"
        );

        double pendingWriting = count("""
                select count(*)
                from public.writing_evaluations evaluation
                left join public.test_attempt_responses response on response.id = evaluation.test_attempt_response_id
                left join public.test_attempts attempt on attempt.id = response.attempt_id
                left join public.test_assignments assignment on assignment.id = attempt.test_assignment_id
                where evaluation.status in ('AI_COMPLETED', 'QUEUED', 'PROCESSING')
                  and (cast(? as uuid) is null or assignment.course_id = cast(? as uuid))
                """, courseId, courseId);
        double pendingReviews = count("""
                select count(*)
                from public.teacher_activity_reviews review
                join public.test_attempts attempt on attempt.id = review.attempt_id
                left join public.test_assignments assignment on assignment.id = attempt.test_assignment_id
                where review.status in ('PENDING', 'REVISION_REQUESTED')
                  and (cast(? as uuid) is null or assignment.course_id = cast(? as uuid))
                """, courseId, courseId);
        double totalPendingGrading = pendingWriting + pendingReviews;
        KpiMetric pendingGrading = new KpiMetric(
                totalPendingGrading,
                String.format("%.0f bài", totalPendingGrading),
                null,
                null,
                "STABLE",
                "Bài Writing hoặc Speaking đang chờ hệ thống xử lý hay giáo viên duyệt",
                "/writing-evaluations"
        );

        double overdueCount = count("""
                select count(*) from public.orders
                where status = 'PENDING_PAYMENT' and expires_at < now()
                  and (cast(? as uuid) is null or course_id = cast(? as uuid))
                """, courseId, courseId);
        KpiMetric overdueInvoices = new KpiMetric(
                overdueCount,
                String.format("%.0f đơn", overdueCount),
                null,
                null,
                "STABLE",
                "Đơn đăng ký khóa học đã quá thời hạn thanh toán",
                "/billing"
        );

        return new ExecutiveKpis(activeStudents, pendingLeads, leadConversion, averageAttendance,
                pendingGrading, overdueInvoices);
    }

    private List<ActionItem> computeActionItems(UUID courseId) {
        List<ActionItem> items = new ArrayList<>();

        long newLeadsCount = count("""
                select count(*) from public.leads
                where status = 'NEW'
                  and (cast(? as uuid) is null or interested_course_id = cast(? as uuid))
                """, courseId, courseId);
        if (newLeadsCount > 0) {
            items.add(new ActionItem(
                    "act-leads", "NEW_LEAD", newLeadsCount + " lead tuyển sinh mới chưa liên hệ",
                    "Lead đang ở trạng thái mới và cần được nhân viên tuyển sinh tiếp nhận.",
                    Math.toIntExact(newLeadsCount), "HIGH", "Trong 24h", "Tư vấn ngay",
                    "/students?tab=leads"
            ));
        }

        long pendingGrading = count("""
                select count(*)
                from public.writing_evaluations evaluation
                left join public.test_attempt_responses response on response.id = evaluation.test_attempt_response_id
                left join public.test_attempts attempt on attempt.id = response.attempt_id
                left join public.test_assignments assignment on assignment.id = attempt.test_assignment_id
                where evaluation.status in ('AI_COMPLETED', 'QUEUED', 'PROCESSING')
                  and (cast(? as uuid) is null or assignment.course_id = cast(? as uuid))
                """, courseId, courseId);
        if (pendingGrading > 0) {
            items.add(new ActionItem(
                    "act-grading", "PENDING_GRADING", pendingGrading + " bài Writing đang chờ xử lý",
                    "Bài làm chưa hoàn tất quy trình đánh giá hoặc chưa được giáo viên duyệt.",
                    Math.toIntExact(pendingGrading), "HIGH", "Hôm nay", "Kiểm tra bài",
                    "/writing-evaluations"
            ));
        }

        long unassignedCount = count("""
                select count(*) from public.courses course
                where course.status in ('OPEN', 'PLANNED', 'ACTIVE') and course.is_active = true
                  and (cast(? as uuid) is null or course.id = cast(? as uuid))
                  and not exists (
                    select 1 from public.course_teachers teacher where teacher.course_id = course.id
                  )
                """, courseId, courseId);
        if (unassignedCount > 0) {
            items.add(new ActionItem(
                    "act-teachers", "UNASSIGNED_TEACHER", unassignedCount + " lớp chưa phân công giáo viên",
                    "Lớp đang mở hoặc hoạt động nhưng chưa có giáo viên phụ trách.",
                    Math.toIntExact(unassignedCount), "HIGH", "Ưu tiên cao", "Phân công GV", "/courses"
            ));
        }

        long overdueOrders = count("""
                select count(*) from public.orders
                where status = 'PENDING_PAYMENT' and expires_at < now()
                  and (cast(? as uuid) is null or course_id = cast(? as uuid))
                """, courseId, courseId);
        if (overdueOrders > 0) {
            items.add(new ActionItem(
                    "act-orders", "OVERDUE_ORDER", overdueOrders + " đơn đăng ký học phí quá hạn",
                    "Đơn đăng ký đã qua thời hạn thanh toán và cần được đối soát.",
                    Math.toIntExact(overdueOrders), "MEDIUM", "Cần rà soát", "Đối soát đơn", "/billing"
            ));
        }

        long overdueSubmissions = count("""
                select count(*)
                from public.test_assignments assignment
                join public.enrollments enrollment
                  on enrollment.course_id = assignment.course_id and enrollment.status = 'ACTIVE'
                where assignment.closes_at < now() and assignment.archived_at is null
                  and (cast(? as uuid) is null or assignment.course_id = cast(? as uuid))
                  and not exists (
                    select 1 from public.test_attempts attempt
                    where attempt.test_assignment_id = assignment.id
                      and attempt.student_id = enrollment.student_id
                      and attempt.status in ('SUBMITTED', 'GRADED')
                  )
                """, courseId, courseId);
        if (overdueSubmissions > 0) {
            items.add(new ActionItem(
                    "act-assignments", "OVERDUE_ASSIGNMENT", overdueSubmissions + " lượt nộp bài đã quá hạn",
                    "Học viên chưa có lượt nộp hoàn tất cho các đề đã đóng hạn.",
                    Math.toIntExact(overdueSubmissions), "LOW", "Theo dõi", "Xem bài tập", "/test-assignments"
            ));
        }

        return items;
    }

    private LearningTrend computeLearningTrend(int rangeDays, UUID courseId) {
        List<TrendPoint> points = jdbc.query("""
                with version_max_scores as (
                  select test_version_id, sum(max_score) max_score
                  from public.test_version_questions
                  group by test_version_id
                ), writing_scores as (
                  select response.attempt_id,
                    avg(coalesce(evaluation.overall_band_teacher, evaluation.overall_band_ai)) score
                  from public.writing_evaluations evaluation
                  join public.test_attempt_responses response on response.id = evaluation.test_attempt_response_id
                  where evaluation.status in ('TEACHER_REVIEWED', 'PUBLISHED')
                  group by response.attempt_id
                ), attempt_values as (
                  select attempt.id, attempt.started_at::date activity_date, version.primary_skill::text skill,
                    case
                      when version.primary_skill in ('READING', 'LISTENING')
                           and maximum.max_score > 0 and coalesce(attempt.final_score, attempt.auto_score) is not null
                        then round(coalesce(attempt.final_score, attempt.auto_score) * 9.0 / maximum.max_score, 2)
                      when version.primary_skill = 'WRITING' then writing.score
                      when version.primary_skill = 'SPEAKING' then review.verified_score
                    end normalized_score
                  from public.test_attempts attempt
                  join public.test_versions version on version.id = attempt.test_version_id
                  left join public.test_assignments assignment on assignment.id = attempt.test_assignment_id
                  left join version_max_scores maximum on maximum.test_version_id = attempt.test_version_id
                  left join writing_scores writing on writing.attempt_id = attempt.id
                  left join public.teacher_activity_reviews review on review.attempt_id = attempt.id
                    and review.status = 'VERIFIED'
                  where attempt.status in ('SUBMITTED', 'GRADED')
                    and attempt.started_at >= current_date - ((? - 1) * interval '1 day')
                    and (cast(? as uuid) is null or assignment.course_id = cast(? as uuid))
                ), attempt_daily as (
                  select activity_date,
                    count(distinct id)::int attempt_count,
                    round(avg(normalized_score), 2) average_score,
                    round(avg(normalized_score) filter (where skill = 'READING'), 2) reading_score,
                    round(avg(normalized_score) filter (where skill = 'LISTENING'), 2) listening_score,
                    round(avg(normalized_score) filter (where skill = 'WRITING'), 2) writing_score,
                    round(avg(normalized_score) filter (where skill = 'SPEAKING'), 2) speaking_score
                  from attempt_values
                  group by activity_date
                ), attendance_daily as (
                  select session.starts_at::date activity_date,
                    round(
                      count(*) filter (where attendance.status in ('PRESENT', 'LATE', 'LEFT_EARLY')) * 100.0
                      / nullif(count(*) filter (where attendance.status in ('PRESENT', 'LATE', 'LEFT_EARLY', 'ABSENT')), 0),
                      2
                    ) attendance_rate
                  from public.attendance_records attendance
                  join public.course_sessions session on session.id = attendance.session_id
                  where session.starts_at >= current_date - ((? - 1) * interval '1 day')
                    and (cast(? as uuid) is null or session.course_id = cast(? as uuid))
                  group by session.starts_at::date
                )
                select
                  to_char(coalesce(attempt.activity_date, attendance.activity_date), 'DD/MM') date_label,
                  coalesce(attempt.activity_date, attendance.activity_date) activity_date,
                  attempt.average_score,
                  coalesce(attempt.attempt_count, 0) attempt_count,
                  attendance.attendance_rate,
                  attempt.reading_score,
                  attempt.listening_score,
                  attempt.writing_score,
                  attempt.speaking_score
                from attempt_daily attempt
                full join attendance_daily attendance on attendance.activity_date = attempt.activity_date
                order by activity_date
                """, (rs, ignored) -> new TrendPoint(
                rs.getString("date_label"),
                rs.getObject("activity_date", LocalDate.class),
                number(rs.getObject("average_score")),
                rs.getInt("attempt_count"),
                number(rs.getObject("attendance_rate")),
                number(rs.getObject("reading_score")),
                number(rs.getObject("listening_score")),
                number(rs.getObject("writing_score")),
                number(rs.getObject("speaking_score"))
        ), rangeDays, courseId, courseId, rangeDays, courseId, courseId);

        Double benchmark = jdbc.queryForObject("""
                select round(avg(profile.target_band), 1)
                from public.student_profiles profile
                join public.enrollments enrollment on enrollment.student_id = profile.user_id
                where enrollment.status = 'ACTIVE' and profile.target_band is not null
                  and (cast(? as uuid) is null or enrollment.course_id = cast(? as uuid))
                """, Double.class, courseId, courseId);
        return new LearningTrend(points, benchmark);
    }

    private List<AtRiskStudent> computeAtRiskStudents(UUID courseId) {
        return jdbc.query("""
                with active_enrollments as (
                  select enrollment.student_id, enrollment.course_id
                  from public.enrollments enrollment
                  where enrollment.status = 'ACTIVE'
                    and (cast(? as uuid) is null or enrollment.course_id = cast(? as uuid))
                ), attendance_stats as (
                  select attendance.student_id, session.course_id,
                    count(*) filter (where attendance.status = 'ABSENT')::int missed_sessions
                  from public.attendance_records attendance
                  join public.course_sessions session on session.id = attendance.session_id
                  group by attendance.student_id, session.course_id
                ), assignment_stats as (
                  select enrollment.student_id, enrollment.course_id,
                    count(assignment.id) filter (
                      where assignment.archived_at is null
                        and coalesce(assignment.opens_at, now()) <= now()
                        and not exists (
                          select 1 from public.test_attempts attempt
                          where attempt.test_assignment_id = assignment.id
                            and attempt.student_id = enrollment.student_id
                            and attempt.status in ('SUBMITTED', 'GRADED')
                        )
                    )::int pending_assignments
                  from active_enrollments enrollment
                  left join public.test_assignments assignment on assignment.course_id = enrollment.course_id
                  group by enrollment.student_id, enrollment.course_id
                )
                select profile.user_id student_id, profile.student_code, person.full_name,
                  course.name course_name, course.code course_code,
                  case
                    when coalesce(attendance.missed_sessions, 0) >= 2
                      then coalesce(attendance.missed_sessions, 0) || ' buổi vắng'
                    when coalesce(assignment.pending_assignments, 0) >= 2
                      then coalesce(assignment.pending_assignments, 0) || ' bài chưa hoàn thành'
                    else 'Band hiện tại còn cách mục tiêu'
                  end risk_reason,
                  case
                    when coalesce(attendance.missed_sessions, 0) >= 3
                      or coalesce(assignment.pending_assignments, 0) >= 3
                      or profile.target_band - profile.current_band >= 2 then 'HIGH'
                    else 'MEDIUM'
                  end risk_level,
                  profile.current_band, profile.target_band,
                  coalesce(attendance.missed_sessions, 0) missed_sessions,
                  coalesce(assignment.pending_assignments, 0) pending_assignments
                from active_enrollments enrollment
                join public.student_profiles profile on profile.user_id = enrollment.student_id
                join public.profiles person on person.id = profile.user_id
                join public.courses course on course.id = enrollment.course_id
                left join attendance_stats attendance
                  on attendance.student_id = enrollment.student_id and attendance.course_id = enrollment.course_id
                left join assignment_stats assignment
                  on assignment.student_id = enrollment.student_id and assignment.course_id = enrollment.course_id
                where coalesce(attendance.missed_sessions, 0) >= 2
                   or coalesce(assignment.pending_assignments, 0) >= 2
                   or (profile.current_band is not null and profile.target_band is not null
                       and profile.target_band - profile.current_band >= 1.5)
                order by
                  case when coalesce(attendance.missed_sessions, 0) >= 3
                         or coalesce(assignment.pending_assignments, 0) >= 3 then 0 else 1 end,
                  greatest(coalesce(attendance.missed_sessions, 0), coalesce(assignment.pending_assignments, 0)) desc,
                  person.full_name
                limit 6
                """, (rs, ignored) -> new AtRiskStudent(
                rs.getObject("student_id", UUID.class),
                rs.getString("student_code"),
                rs.getString("full_name"),
                rs.getString("course_name"),
                rs.getString("course_code"),
                rs.getString("risk_reason"),
                rs.getString("risk_level"),
                number(rs.getObject("current_band")),
                number(rs.getObject("target_band")),
                rs.getInt("missed_sessions"),
                rs.getInt("pending_assignments"),
                "/students/" + rs.getObject("student_id", UUID.class)
        ), courseId, courseId);
    }

    private List<ScheduleEvent> computeScheduleEvents(UUID courseId) {
        return jdbc.query("""
                    (
                      select
                        cs.id::text event_id,
                        'CLASS_SESSION' event_type,
                        cs.title,
                        c.name course_name,
                        c.code course_code,
                        cs.starts_at,
                        cs.ends_at,
                        coalesce(st.full_name, pt.full_name) teacher_name,
                        coalesce(cs.zoom_url, c.default_zoom_url) location_or_zoom,
                        cs.status::text status_badge
                      from public.course_sessions cs
                      join public.courses c on c.id = cs.course_id
                      left join public.profiles st on st.id = cs.teacher_id
                      left join public.course_teachers cta on cta.course_id = c.id and cta.is_primary = true
                      left join public.profiles pt on pt.id = cta.teacher_id
                      where cs.starts_at between now() and now() + interval '7 days'
                        and (cast(? as uuid) is null or cs.course_id = cast(? as uuid))
                    )
                    union all
                    (
                      select
                        c.id::text event_id,
                        'COURSE_START' event_type,
                        'Khai giảng: ' || c.name title,
                        c.name course_name,
                        c.code course_code,
                        (c.starts_on::text || ' 00:00:00')::timestamptz starts_at,
                        (c.starts_on::text || ' 23:59:59')::timestamptz ends_at,
                        null teacher_name,
                        c.default_zoom_url location_or_zoom,
                        c.status::text status_badge
                      from public.courses c
                      where c.starts_on between current_date and current_date + 7
                        and (cast(? as uuid) is null or c.id = cast(? as uuid))
                    )
                    order by starts_at asc
                    limit 8
                    """, (rs, i) -> new ScheduleEvent(
                    rs.getString("event_id"),
                    rs.getString("event_type"),
                    rs.getString("title"),
                    rs.getString("course_name"),
                    rs.getString("course_code"),
                    rs.getObject("starts_at", OffsetDateTime.class),
                    rs.getObject("ends_at", OffsetDateTime.class),
                    rs.getString("teacher_name"),
                    rs.getString("location_or_zoom"),
                    rs.getString("status_badge")
            ), courseId, courseId, courseId, courseId);
    }

    private List<CoursePerformance> computeCoursePerformances(UUID courseId) {
        return jdbc.query("""
                    with version_max_scores as (
                      select test_version_id, sum(max_score) max_score
                      from public.test_version_questions
                      group by test_version_id
                    )
                    select
                      c.id course_id,
                      c.code course_code,
                      c.name course_name,
                      (select count(distinct e.student_id)::int from public.enrollments e where e.course_id = c.id and e.status = 'ACTIVE') current_enrollments,
                      c.capacity,
                      (select round(
                          count(*) filter (where attendance.status in ('PRESENT', 'LATE', 'LEFT_EARLY')) * 100.0
                          / nullif(count(*) filter (where attendance.status in ('PRESENT', 'LATE', 'LEFT_EARLY', 'ABSENT')), 0)
                        )::int
                       from public.attendance_records attendance
                       join public.course_sessions session on session.id = attendance.session_id
                       where session.course_id = c.id) attendance_rate,
                      coalesce(
                        (select round(count(distinct s.id) filter (where s.status = 'COMPLETED') * 100.0 / nullif(c.total_sessions, 0))
                         from public.course_sessions s where s.course_id = c.id)::int,
                        0
                      ) completion_progress,
                      (select round(avg(coalesce(attempt.final_score, attempt.auto_score) * 9.0 / maximum.max_score), 1)
                       from public.test_attempts attempt
                       join public.test_assignments assignment on assignment.id = attempt.test_assignment_id
                       join version_max_scores maximum on maximum.test_version_id = attempt.test_version_id
                       where assignment.course_id = c.id and maximum.max_score > 0
                         and attempt.status in ('SUBMITTED', 'GRADED')
                         and coalesce(attempt.final_score, attempt.auto_score) is not null) average_score,
                      (select count(distinct enrollment.student_id)::int
                       from public.enrollments enrollment
                       join public.student_profiles profile on profile.user_id = enrollment.student_id
                       where enrollment.course_id = c.id and enrollment.status = 'ACTIVE'
                         and (
                           (profile.current_band is not null and profile.target_band is not null
                             and profile.target_band - profile.current_band >= 1.5)
                           or (select count(*) from public.attendance_records attendance
                               join public.course_sessions session on session.id = attendance.session_id
                               where session.course_id = c.id and attendance.student_id = enrollment.student_id
                                 and attendance.status = 'ABSENT') >= 2
                           or (select count(*) from public.test_assignments assignment
                               where assignment.course_id = c.id and assignment.archived_at is null
                                 and coalesce(assignment.opens_at, now()) <= now()
                                 and not exists (
                                   select 1 from public.test_attempts attempt
                                   where attempt.test_assignment_id = assignment.id
                                     and attempt.student_id = enrollment.student_id
                                     and attempt.status in ('SUBMITTED', 'GRADED')
                                 )) >= 2
                         )) at_risk_count,
                      c.status::text operational_status
                    from public.courses c
                    where c.is_active = true
                      and (cast(? as uuid) is null or c.id = cast(? as uuid))
                    order by c.starts_on desc
                    """, (rs, i) -> new CoursePerformance(
                    rs.getObject("course_id", UUID.class),
                    rs.getString("course_code"),
                    rs.getString("course_name"),
                    rs.getInt("current_enrollments"),
                    rs.getInt("capacity"),
                    rs.getObject("attendance_rate", Integer.class),
                    rs.getInt("completion_progress"),
                    number(rs.getObject("average_score")),
                    rs.getInt("at_risk_count"),
                    rs.getString("operational_status")
            ), courseId, courseId);
    }

    private List<CourseOption> fetchCourseFilterOptions() {
        return jdbc.query("""
                    select id, code, name
                    from public.courses
                    where is_active = true
                    order by code asc
                    """, (rs, i) -> new CourseOption(
                    rs.getObject("id", UUID.class),
                    rs.getString("code"),
                    rs.getString("name")
            ));
    }

    private Ratio leadConversion(int rangeDays, int offsetDays, UUID courseId) {
        return jdbc.query("""
                select
                  count(*) filter (where status = 'CONVERTED' or converted_student_id is not null) numerator,
                  count(*) denominator
                from public.leads
                where created_at >= now() - ((? + ?) * interval '1 day')
                  and created_at < now() - (? * interval '1 day')
                  and (cast(? as uuid) is null or interested_course_id = cast(? as uuid))
                """, (rs, ignored) -> new Ratio(rs.getLong("numerator"), rs.getLong("denominator")),
                rangeDays, offsetDays, offsetDays, courseId, courseId).getFirst();
    }

    private Ratio attendanceRatio(int rangeDays, int offsetDays, UUID courseId) {
        return jdbc.query("""
                select
                  count(*) filter (where attendance.status in ('PRESENT', 'LATE', 'LEFT_EARLY')) numerator,
                  count(*) filter (where attendance.status in ('PRESENT', 'LATE', 'LEFT_EARLY', 'ABSENT')) denominator
                from public.attendance_records attendance
                join public.course_sessions session on session.id = attendance.session_id
                where session.starts_at >= now() - ((? + ?) * interval '1 day')
                  and session.starts_at < now() - (? * interval '1 day')
                  and (cast(? as uuid) is null or session.course_id = cast(? as uuid))
                """, (rs, ignored) -> new Ratio(rs.getLong("numerator"), rs.getLong("denominator")),
                rangeDays, offsetDays, offsetDays, courseId, courseId).getFirst();
    }

    private long count(String sql, Object... args) {
        return Optional.ofNullable(jdbc.queryForObject(sql, Long.class, args)).orElse(0L);
    }

    private Double percentChange(double current, Double previous) {
        if (previous == null || previous == 0) return null;
        return Math.round(((current - previous) * 1000.0 / previous)) / 10.0;
    }

    private String changeType(Double change) {
        if (change == null || change == 0) return "STABLE";
        return change > 0 ? "INCREASE" : "DECREASE";
    }

    private Double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private record Ratio(long numerator, long denominator) {
        double percentage() {
            return denominator == 0 ? 0 : Math.round(numerator * 1000.0 / denominator) / 10.0;
        }
    }

    private record LegacyData(
            long activeEnrollments,
            long openCourses,
            long activeCourses,
            long totalCourses,
            List<UpcomingCourse> upcomingCourses
    ) {}

    private record DashboardRow(
            long activeEnrollments,
            long openCourses,
            long activeCourses,
            long totalCourses,
            UUID id,
            String code,
            String name,
            Short capacity,
            LocalDate startsOn,
            String status
    ) {}
}
