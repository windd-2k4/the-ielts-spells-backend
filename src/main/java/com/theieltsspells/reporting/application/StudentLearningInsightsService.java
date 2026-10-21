package com.theieltsspells.reporting.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.reporting.application.dto.StudentAttemptDetailResponse;
import com.theieltsspells.reporting.application.dto.StudentAttemptDetailResponse.QuestionDetail;
import com.theieltsspells.reporting.application.dto.StudentLearningInsightsResponse;
import com.theieltsspells.reporting.application.dto.StudentLearningInsightsResponse.MonthlyCompletedAttempts;
import com.theieltsspells.reporting.application.dto.StudentLearningInsightsResponse.RecurringMistake;
import com.theieltsspells.reporting.application.dto.StudentLearningInsightsResponse.SkillInsight;
import com.theieltsspells.reporting.application.dto.StudentLearningInsightsResponse.Summary;
import com.theieltsspells.reporting.application.dto.StudentLearningInsightsResponse.TestAttemptInsight;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentLearningInsightsService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

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
        return new StudentLearningInsightsResponse(summary(studentId, mistakes), skills, monthlyCompletedAttempts(studentId), attempts, mistakes);
    }

    private List<MonthlyCompletedAttempts> monthlyCompletedAttempts(UUID studentId) {
        return jdbc.query("""
                with months as (
                  select generate_series(
                    date_trunc('month', now() at time zone 'Asia/Ho_Chi_Minh') - interval '5 months',
                    date_trunc('month', now() at time zone 'Asia/Ho_Chi_Minh'),
                    interval '1 month'
                  )::date month_bucket
                ), skill_names(skill) as (
                  values ('LISTENING'), ('READING'), ('WRITING'), ('SPEAKING')
                ), monthly_counts as (
                  select date_trunc('month', coalesce(attempt.submitted_at, attempt.last_saved_at, attempt.started_at)
                    at time zone 'Asia/Ho_Chi_Minh')::date month_bucket,
                    version.primary_skill::text skill, count(*)::int completed_attempts
                  from public.test_attempts attempt
                  join public.test_versions version on version.id = attempt.test_version_id
                  where attempt.student_id = ? and attempt.status in ('SUBMITTED', 'GRADED', 'EXPIRED')
                  group by 1, 2
                )
                select to_char(months.month_bucket, 'YYYY-MM') month_key, skill_names.skill,
                  coalesce(monthly_counts.completed_attempts, 0) completed_attempts
                from months cross join skill_names
                left join monthly_counts on monthly_counts.month_bucket = months.month_bucket
                  and monthly_counts.skill = skill_names.skill
                order by months.month_bucket, skill_names.skill
                """, (rs, ignored) -> new MonthlyCompletedAttempts(
                rs.getString("month_key"), rs.getString("skill"), rs.getInt("completed_attempts")
        ), studentId);
    }

    public StudentAttemptDetailResponse getAttemptDetail(UUID studentId, UUID attemptId) {
        List<AttemptDetailHeader> headers = jdbc.query("""
                select attempt.id, attempt.test_version_id, version.title, version.primary_skill::text skill,
                  attempt.status::text status, attempt.attempt_origin origin, attempt.attempt_no,
                  attempt.started_at, attempt.submitted_at,
                  coalesce(attempt.submitted_at, attempt.last_saved_at, attempt.started_at) last_activity_at,
                  coalesce(attempt.final_score, attempt.auto_score) score,
                  coalesce(assignment.duration_seconds, version.duration_minutes * 60)::bigint allowed_duration_seconds,
                  case
                    when attempt.attempt_origin = 'SELF_PRACTICE' and attempt.expires_at is not null then
                      greatest(0, least(
                        version.duration_minutes * 60::bigint,
                        version.duration_minutes * 60::bigint - least(
                          version.duration_minutes * 60::bigint,
                          coalesce(
                            attempt.paused_remaining_seconds,
                            greatest(0, extract(epoch from (
                              attempt.expires_at - coalesce(attempt.submitted_at, attempt.last_saved_at)
                            ))::bigint)
                          )
                        )
                      ))
                    else greatest(0, least(
                      coalesce(assignment.duration_seconds, version.duration_minutes * 60)::bigint,
                      extract(epoch from (
                        coalesce(attempt.submitted_at, attempt.last_saved_at) - attempt.started_at
                      ))::bigint
                    ))
                  end duration_seconds
                from public.test_attempts attempt
                join public.test_versions version on version.id = attempt.test_version_id
                left join public.test_assignments assignment on assignment.id = attempt.test_assignment_id
                where attempt.id = ? and attempt.student_id = ?
                """, (rs, ignored) -> new AttemptDetailHeader(
                rs.getObject("id", UUID.class),
                rs.getObject("test_version_id", UUID.class),
                rs.getString("title"),
                rs.getString("skill"),
                rs.getString("status"),
                rs.getString("origin"),
                rs.getShort("attempt_no"),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("submitted_at", OffsetDateTime.class),
                rs.getObject("last_activity_at", OffsetDateTime.class),
                rs.getLong("duration_seconds"),
                rs.getLong("allowed_duration_seconds"),
                rs.getBigDecimal("score")
        ), attemptId, studentId);
        if (headers.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy lượt làm của học viên");
        }

        AttemptDetailHeader header = headers.getFirst();
        List<QuestionDetail> questions = attemptQuestions(header.versionId(), attemptId);
        int correctCount = (int) questions.stream().filter(item -> Boolean.TRUE.equals(item.correct())).count();
        int incorrectCount = (int) questions.stream().filter(item -> Boolean.FALSE.equals(item.correct())).count();
        int unansweredCount = (int) questions.stream().filter(item -> item.studentAnswers().isEmpty()).count();
        BigDecimal maxScore = questions.stream().map(QuestionDetail::maxScore)
                .filter(value -> value != null).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new StudentAttemptDetailResponse(
                header.id(), header.title(), header.skill(), header.status(), header.origin(), header.attemptNo(),
                header.startedAt(), header.submittedAt(), header.lastActivityAt(), header.durationSeconds(),
                header.allowedDurationSeconds(), header.score(), maxScore, correctCount, incorrectCount,
                unansweredCount, questions
        );
    }

    private List<QuestionDetail> attemptQuestions(UUID versionId, UUID attemptId) {
        return jdbc.query("""
                with items as (
                  select question.question_key, question.question_no::int question_no,
                    question.type_format question_type, section.title section_title,
                    question_group.title group_title, question.prompt,
                    question.correct_answers::text correct_answers, question.explanation,
                    question.max_score,
                    coalesce((
                      select jsonb_object_agg(option.option_key,
                        case
                          when nullif(trim(option.option_code), '') is null then option.content
                          else option.option_code || ' — ' || option.content
                        end)
                      from public.test_version_question_options option
                      where option.group_id = question.group_id
                    ), '{}'::jsonb)::text option_map
                  from public.test_version_questions question
                  join public.test_version_question_groups question_group on question_group.id = question.group_id
                  join public.test_version_sections section on section.id = question_group.section_id
                  where question.test_version_id = ?
                  union all
                  select response.question_key, null, response.question_type, null, null, null,
                    '[]', null, response.max_score, '{}'
                  from public.test_attempt_responses response
                  where response.attempt_id = ? and not exists (
                    select 1 from public.test_version_questions question
                    where question.test_version_id = ? and question.question_key = response.question_key
                  )
                )
                select item.question_key,
                  row_number() over(order by item.question_no nulls last, item.question_key)::int display_no,
                  item.question_type, item.section_title, item.group_title, item.prompt,
                  item.correct_answers, item.explanation, item.max_score, item.option_map,
                  response.answer::text student_answer, response.is_correct,
                  coalesce(response.auto_score, 0) score
                from items item
                left join public.test_attempt_responses response
                  on response.attempt_id = ? and response.question_key = item.question_key
                order by item.question_no nulls last, item.question_key
                """, (rs, ignored) -> new QuestionDetail(
                rs.getString("question_key"),
                rs.getInt("display_no"),
                rs.getString("question_type"),
                rs.getString("section_title"),
                rs.getString("group_title"),
                rs.getString("prompt"),
                displayAnswers(answerValues(rs.getString("student_answer")), rs.getString("option_map")),
                displayAnswers(answerValues(rs.getString("correct_answers")), rs.getString("option_map")),
                rs.getObject("is_correct", Boolean.class),
                rs.getBigDecimal("score"),
                rs.getBigDecimal("max_score"),
                rs.getString("explanation")
        ), versionId, attemptId, versionId, attemptId);
    }

    private List<String> answerValues(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode value = root.isObject()
                    ? root.has("values") ? root.get("values")
                    : root.has("value") ? root.get("value")
                    : root.has("text") ? root.get("text") : root
                    : root;
            if (value == null || value.isNull()) return List.of();
            if (value.isArray()) {
                List<String> result = new ArrayList<>();
                value.forEach(item -> {
                    if (!item.isNull() && !item.asText().isBlank()) result.add(item.asText());
                });
                return List.copyOf(result);
            }
            return value.asText().isBlank() ? List.of() : List.of(value.asText());
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<String> displayAnswers(List<String> values, String optionMapJson) {
        if (values.isEmpty() || optionMapJson == null || optionMapJson.isBlank()) return values;
        try {
            JsonNode optionMap = objectMapper.readTree(optionMapJson);
            return values.stream().map(value -> {
                JsonNode label = optionMap.get(value);
                return label == null || label.isNull() || label.asText().isBlank() ? value : label.asText();
            }).toList();
        } catch (Exception exception) {
            return values;
        }
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
                with recorded_activity as (
                  select coalesce(sum(coalesce(activity.duration_seconds, 0)), 0)::bigint seconds
                  from public.student_activity_attempts activity
                  where activity.student_id = ?
                ), recorded_attendance as (
                  select coalesce(sum(coalesce(
                    attendance.duration_seconds,
                    extract(epoch from (course_session.ends_at - course_session.starts_at))::bigint
                  )), 0)::bigint seconds
                  from public.attendance_records attendance
                  join public.course_sessions course_session on course_session.id = attendance.session_id
                  where attendance.student_id = ?
                    and attendance.status in ('PRESENT', 'LATE', 'LEFT_EARLY')
                ), recorded_tests as (
                  select coalesce(sum(
                    case
                      when attempt.attempt_origin = 'SELF_PRACTICE'
                        and attempt.expires_at is not null then
                        greatest(0, least(
                          version.duration_minutes * 60::bigint,
                          version.duration_minutes * 60::bigint - least(
                            version.duration_minutes * 60::bigint,
                            coalesce(
                              attempt.paused_remaining_seconds,
                              greatest(0, extract(epoch from (
                                attempt.expires_at - coalesce(attempt.submitted_at, attempt.last_saved_at)
                              ))::bigint)
                            )
                          )
                        ))
                      else greatest(0, least(
                        coalesce(assignment.duration_seconds, version.duration_minutes * 60)::bigint,
                        extract(epoch from (
                          coalesce(attempt.submitted_at, attempt.last_saved_at) - attempt.started_at
                        ))::bigint
                      ))
                    end
                  ), 0)::bigint seconds
                  from public.test_attempts attempt
                  join public.test_versions version on version.id = attempt.test_version_id
                  left join public.test_assignments assignment on assignment.id = attempt.test_assignment_id
                  where attempt.student_id = ?
                    and not exists (
                      select 1 from public.student_activity_attempts activity
                      where activity.test_attempt_id = attempt.id
                    )
                )
                select recorded_activity.seconds + recorded_attendance.seconds + recorded_tests.seconds
                from recorded_activity, recorded_attendance, recorded_tests
                """, Long.class, studentId, studentId, studentId);
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

    private record AttemptDetailHeader(
            UUID id,
            UUID versionId,
            String title,
            String skill,
            String status,
            String origin,
            short attemptNo,
            OffsetDateTime startedAt,
            OffsetDateTime submittedAt,
            OffsetDateTime lastActivityAt,
            long durationSeconds,
            long allowedDurationSeconds,
            BigDecimal score
    ) {}

    record SupportAssessment(String level, List<String> reasons) {}
}
