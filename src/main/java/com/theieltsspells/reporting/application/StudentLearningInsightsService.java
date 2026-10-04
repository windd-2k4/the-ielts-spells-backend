package com.theieltsspells.reporting.application;

import com.theieltsspells.reporting.application.dto.StudentLearningInsightsResponse;
import com.theieltsspells.reporting.application.dto.StudentLearningInsightsResponse.RecurringMistake;
import com.theieltsspells.reporting.application.dto.StudentLearningInsightsResponse.SkillInsight;
import com.theieltsspells.reporting.application.dto.StudentLearningInsightsResponse.Summary;
import com.theieltsspells.reporting.application.dto.StudentLearningInsightsResponse.TestAttemptInsight;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentLearningInsightsService {

    private final JdbcTemplate jdbc;

    public StudentLearningInsightsResponse get(UUID studentId) {
        Boolean exists = jdbc.queryForObject(
                "select exists(select 1 from public.student_profiles where user_id = ?)",
                Boolean.class,
                studentId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new ResourceNotFoundException("Không tìm thấy học viên");
        }

        List<TestAttemptInsight> attempts = attempts(studentId);
        List<SkillInsight> skills = skills(studentId);
        List<RecurringMistake> mistakes = recurringMistakes(studentId);
        return new StudentLearningInsightsResponse(summary(studentId, mistakes), skills, attempts, mistakes);
    }

    private List<TestAttemptInsight> attempts(UUID studentId) {
        return jdbc.query("""
                select att.id, version.title, version.primary_skill::text skill,
                  att.status::text status, att.attempt_origin origin,
                  course.code course_code, course.name course_name, att.attempt_no,
                  att.started_at, att.submitted_at,
                  coalesce(att.submitted_at, att.last_saved_at, att.started_at) last_activity_at,
                  coalesce(att.final_score, att.auto_score) score,
                  coalesce(sum(question.max_score), 0) max_score,
                  count(response.id) filter (where response.is_correct = true)::int correct_count,
                  count(response.id) filter (where response.is_correct = false)::int incorrect_count,
                  greatest(count(question.id) - count(response.id), 0)::int unanswered_count,
                  case when count(response.id) > 0 then
                    round(100.0 * count(response.id) filter (where response.is_correct = true) / count(response.id))::int
                  end accuracy
                from public.test_attempts att
                join public.test_versions version on version.id = att.test_version_id
                left join public.test_assignments assignment on assignment.id = att.test_assignment_id
                left join public.courses course on course.id = assignment.course_id
                left join public.test_version_questions question on question.test_version_id = version.id
                left join public.test_attempt_responses response
                  on response.attempt_id = att.id and response.question_key = question.question_key
                where att.student_id = ?
                group by att.id, version.title, version.primary_skill, course.code, course.name
                order by last_activity_at desc
                limit 50
                """, (rs, ignored) -> new TestAttemptInsight(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("skill"),
                rs.getString("status"),
                rs.getString("origin"),
                rs.getString("course_code"),
                rs.getString("course_name"),
                rs.getShort("attempt_no"),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("submitted_at", OffsetDateTime.class),
                rs.getObject("last_activity_at", OffsetDateTime.class),
                rs.getBigDecimal("score"),
                rs.getBigDecimal("max_score"),
                rs.getInt("correct_count"),
                rs.getInt("incorrect_count"),
                rs.getInt("unanswered_count"),
                rs.getObject("accuracy", Integer.class)
        ), studentId);
    }

    private List<SkillInsight> skills(UUID studentId) {
        return jdbc.query("""
                with attempt_stats as (
                  select att.id, version.primary_skill::text skill,
                    coalesce(att.final_score, att.auto_score) score,
                    coalesce(sum(question.max_score), 0) max_score,
                    count(response.id)::int answered,
                    count(response.id) filter (where response.is_correct = true)::int correct
                  from public.test_attempts att
                  join public.test_versions version on version.id = att.test_version_id
                  left join public.test_version_questions question on question.test_version_id = version.id
                  left join public.test_attempt_responses response
                    on response.attempt_id = att.id and response.question_key = question.question_key
                  where att.student_id = ? and att.status in ('SUBMITTED', 'GRADED', 'EXPIRED')
                  group by att.id, version.primary_skill
                )
                select skill, count(*)::int attempts, coalesce(sum(answered), 0)::int answered,
                  coalesce(sum(correct), 0)::int correct,
                  case when sum(answered) > 0 then round(100.0 * sum(correct) / sum(answered))::int end accuracy,
                  case when count(*) filter (where score is not null and max_score > 0) > 0 then
                    round(avg(100.0 * score / nullif(max_score, 0))
                      filter (where score is not null and max_score > 0))::int
                  end average_score_percent
                from attempt_stats
                group by skill
                order by skill
                """, (rs, ignored) -> new SkillInsight(
                rs.getString("skill"),
                rs.getInt("attempts"),
                rs.getInt("answered"),
                rs.getInt("correct"),
                rs.getObject("accuracy", Integer.class),
                rs.getObject("average_score_percent", Integer.class)
        ), studentId);
    }

    private List<RecurringMistake> recurringMistakes(UUID studentId) {
        return jdbc.query("""
                select coalesce(response.question_type, question.type_format, 'UNKNOWN') question_type,
                  version.primary_skill::text skill, count(*)::int error_count,
                  count(distinct attempt.id)::int affected_attempts
                from public.test_attempt_responses response
                join public.test_attempts attempt on attempt.id = response.attempt_id
                join public.test_versions version on version.id = attempt.test_version_id
                left join public.test_version_questions question
                  on question.test_version_id = attempt.test_version_id
                  and question.question_key = response.question_key
                where attempt.student_id = ? and response.is_correct = false
                group by coalesce(response.question_type, question.type_format, 'UNKNOWN'), version.primary_skill
                having count(*) >= 2
                order by error_count desc, affected_attempts desc
                limit 8
                """, (rs, ignored) -> {
            String type = rs.getString("question_type");
            return new RecurringMistake(
                    friendlyQuestionType(type),
                    rs.getString("skill"),
                    rs.getInt("error_count"),
                    rs.getInt("affected_attempts"),
                    recommendation(type));
        }, studentId);
    }

    private Summary summary(UUID studentId, List<RecurringMistake> mistakes) {
        SummaryMetrics metrics = jdbc.queryForObject("""
                with attempt_stats as (
                  select attempt.id, attempt.status::text status,
                    coalesce(attempt.submitted_at, attempt.last_saved_at, attempt.started_at) last_activity_at,
                    coalesce(attempt.final_score, attempt.auto_score) score,
                    coalesce(sum(question.max_score), 0) max_score,
                    count(response.id)::int answered,
                    count(response.id) filter (where response.is_correct = true)::int correct
                  from public.test_attempts attempt
                  join public.test_versions version on version.id = attempt.test_version_id
                  left join public.test_version_questions question on question.test_version_id = version.id
                  left join public.test_attempt_responses response
                    on response.attempt_id = attempt.id and response.question_key = question.question_key
                  where attempt.student_id = ?
                  group by attempt.id
                )
                select count(*)::int total_attempts,
                  count(*) filter (where status <> 'IN_PROGRESS')::int completed_attempts,
                  count(*) filter (where status = 'IN_PROGRESS')::int in_progress_attempts,
                  case when sum(answered) > 0 then round(100.0 * sum(correct) / sum(answered))::int end average_accuracy,
                  case when count(*) filter (where score is not null and max_score > 0) > 0 then
                    round(avg(100.0 * score / nullif(max_score, 0))
                      filter (where score is not null and max_score > 0))::int
                  end average_score_percent,
                  max(last_activity_at) last_activity_at
                from attempt_stats
                """, (rs, ignored) -> new SummaryMetrics(
                rs.getInt("total_attempts"),
                rs.getInt("completed_attempts"),
                rs.getInt("in_progress_attempts"),
                rs.getObject("average_accuracy", Integer.class),
                rs.getObject("average_score_percent", Integer.class),
                rs.getObject("last_activity_at", OffsetDateTime.class)
        ), studentId);
        if (metrics == null) metrics = new SummaryMetrics(0, 0, 0, null, null, null);

        SupportAssessment support = assessSupport(metrics.completedAttempts(), metrics.inProgressAttempts(),
                metrics.averageAccuracy(), metrics.lastActivityAt(), !mistakes.isEmpty(), OffsetDateTime.now());

        return new Summary(metrics.totalAttempts(), metrics.completedAttempts(), metrics.inProgressAttempts(),
                totalStudyMinutes(studentId), metrics.averageAccuracy(), metrics.averageScorePercent(), metrics.lastActivityAt(),
                support.level(), support.reasons());
    }

    private long totalStudyMinutes(UUID studentId) {
        Long seconds = jdbc.queryForObject("""
                select
                  coalesce((
                    select sum(coalesce(activity.duration_seconds, 0))
                    from public.student_activity_attempts activity
                    where activity.student_id = ?
                  ), 0)
                  + coalesce((
                    select sum(coalesce(
                      attendance.duration_seconds,
                      extract(epoch from (course_session.ends_at - course_session.starts_at))::bigint
                    ))
                    from public.attendance_records attendance
                    join public.course_sessions course_session on course_session.id = attendance.session_id
                    where attendance.student_id = ?
                      and attendance.status in ('PRESENT', 'LATE', 'LEFT_EARLY')
                  ), 0)
                """, Long.class, studentId, studentId);
        return seconds == null ? 0 : Math.round(seconds / 60.0);
    }

    static SupportAssessment assessSupport(
            int completedAttempts,
            int inProgressAttempts,
            Integer averageAccuracy,
            OffsetDateTime lastActivityAt,
            boolean hasRecurringMistakes,
            OffsetDateTime now
    ) {
        List<String> reasons = new ArrayList<>();
        if (completedAttempts == 0) reasons.add("Chưa có bài làm hoàn tất để đánh giá xu hướng.");
        if (averageAccuracy != null && averageAccuracy < 60) reasons.add("Độ chính xác trung bình dưới 60%.");
        if (lastActivityAt != null && lastActivityAt.isBefore(now.minusDays(14))) {
            reasons.add("Không có hoạt động làm bài trong 14 ngày gần đây.");
        }
        if (inProgressAttempts >= 2) reasons.add("Có nhiều bài làm đang dang dở.");
        if (hasRecurringMistakes) reasons.add("Có dạng lỗi lặp lại cần luyện tập có trọng tâm.");

        String level;
        if (completedAttempts == 0) level = "NOT_ENOUGH_DATA";
        else if ((averageAccuracy != null && averageAccuracy < 50)
                || (lastActivityAt != null && lastActivityAt.isBefore(now.minusDays(21)))) {
            level = "NEEDS_ATTENTION";
        } else if (!reasons.isEmpty()) level = "WATCH";
        else level = "ON_TRACK";
        return new SupportAssessment(level, List.copyOf(reasons));
    }

    private String friendlyQuestionType(String value) {
        return switch (value) {
            case "TRUE_FALSE_NOT_GIVEN" -> "True / False / Not Given";
            case "MATCHING_HEADINGS" -> "Matching Headings";
            case "MULTIPLE_CHOICE" -> "Multiple Choice";
            case "SUMMARY_COMPLETION" -> "Summary Completion";
            case "SENTENCE_COMPLETION" -> "Sentence Completion";
            case "MATCHING_INFORMATION" -> "Matching Information";
            case "SHORT_ANSWER" -> "Short Answer";
            default -> value.replace('_', ' ');
        };
    }

    private String recommendation(String value) {
        return switch (value) {
            case "TRUE_FALSE_NOT_GIVEN" -> "Đối chiếu từng mệnh đề với bằng chứng trong bài và phân biệt False với Not Given.";
            case "MATCHING_HEADINGS" -> "Tóm tắt ý chính mỗi đoạn trước khi đối chiếu với danh sách tiêu đề.";
            case "SUMMARY_COMPLETION", "SENTENCE_COMPLETION" -> "Kiểm tra giới hạn số từ, từ loại và chính tả trước khi nộp.";
            case "MATCHING_INFORMATION" -> "Đánh dấu từ khóa và xác định đoạn chứa bằng chứng trước khi chọn đáp án.";
            default -> "Xem lại câu sai, ghi lại từ khóa gây nhầm và làm lại dạng bài tương ứng.";
        };
    }

    private record SummaryMetrics(
            int totalAttempts,
            int completedAttempts,
            int inProgressAttempts,
            Integer averageAccuracy,
            Integer averageScorePercent,
            OffsetDateTime lastActivityAt
    ) {}

    record SupportAssessment(String level, List<String> reasons) {}
}
