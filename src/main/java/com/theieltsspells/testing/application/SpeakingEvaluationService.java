package com.theieltsspells.testing.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.academic.application.AcademicMembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpeakingEvaluationService {
    public static final String ATTEMPT_KEY = "__ATTEMPT__";
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AcademicMembershipService memberships;

    @Transactional
    public void registerAttempt(UUID attemptId, UUID studentId) {
        var responses = responseSources(attemptId);
        String transcript = responses.stream().filter(value -> value.transcript() != null && !value.transcript().isBlank())
                .map(value -> value.questionKey() + ": " + value.transcript().trim())
                .reduce((left, right) -> left + "\n\n" + right).orElse("");
        String firstAudio = responses.stream().map(ResponseSource::audioUrl)
                .filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
        jdbc.update("""
                insert into public.speaking_evaluations(attempt_id, student_id, question_key, audio_url,
                  transcript, status, rubric_version, prompt_version)
                values (?, ?, ?, ?, ?, 'NOT_ELIGIBLE', 'ielts-speaking-public-v1', 'speaking-evaluator-v2')
                on conflict (attempt_id, question_key) do update set transcript=excluded.transcript,
                  audio_url=excluded.audio_url, updated_at=now()
                """, attemptId, studentId, ATTEMPT_KEY, firstAudio, transcript);
    }

    @Transactional
    public int requestAi(UUID attemptId, UUID studentId) {
        var attempts = jdbc.queryForList("""
                select attempt.id from public.test_attempts attempt
                join public.test_versions version on version.id=attempt.test_version_id
                where attempt.id=? and attempt.student_id=? and attempt.status <> 'IN_PROGRESS'
                  and version.primary_skill='SPEAKING' for update of attempt
                """, UUID.class, attemptId, studentId);
        if (attempts.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy bài Speaking đã nộp");
        var assignments = jdbc.queryForList("""
                select assignment.course_id, assignment.show_result_after_submit
                from public.test_attempts attempt join public.test_assignments assignment
                  on assignment.id=attempt.test_assignment_id where attempt.id=?
                """, attemptId);
        if (!assignments.isEmpty()) {
            var assignment = assignments.getFirst();
            if (!memberships.hasActiveEnrollment((UUID) assignment.get("course_id"), studentId)
                    || !Boolean.TRUE.equals(assignment.get("show_result_after_submit"))) {
                throw new BusinessRuleException("Bài trong khóa chưa khả dụng để yêu cầu TIS chấm");
            }
        }
        jdbc.queryForObject("select user_id from public.student_profiles where user_id=? for update", UUID.class, studentId);
        registerAttempt(attemptId, studentId);
        var requestable = jdbc.queryForList("""
                select id from public.speaking_evaluations
                where attempt_id=? and question_key=? and status in ('NOT_ELIGIBLE','FAILED') for update
                """, UUID.class, attemptId, ATTEMPT_KEY);
        if (requestable.isEmpty()) return 0;
        if (!isAiEligible(studentId)) {
            throw new BusinessRuleException("Bạn chưa được cấp lượt chấm Speaking bằng TIS hoặc đã dùng hết ngân sách tháng này");
        }
        return jdbc.update("""
                update public.speaking_evaluations set status='QUEUED', error_message=null,
                  ai_requested_at=now(), processing_token=null, processing_started_at=null, updated_at=now()
                where attempt_id=? and question_key=? and status in ('NOT_ELIGIBLE','FAILED')
                """, attemptId, ATTEMPT_KEY);
    }

    public boolean isAiEligible(UUID studentId) {
        var rows = jdbc.query("""
                select unlimited, monthly_limit from public.speaking_ai_entitlements
                where student_id=? and enabled and active_from <= now()
                  and (expires_at is null or expires_at > now())
                """, (rs, ignored) -> new Entitlement(rs.getBoolean("unlimited"),
                rs.getObject("monthly_limit", Integer.class)), studentId);
        if (rows.isEmpty()) return false;
        if (rows.getFirst().unlimited()) return true;
        Long used = jdbc.queryForObject("""
                select count(*) from public.speaking_evaluations
                where student_id=? and question_key=? and ai_requested_at >= date_trunc('month', now())
                """, Long.class, studentId, ATTEMPT_KEY);
        return used != null && rows.getFirst().monthlyLimit() != null && used < rows.getFirst().monthlyLimit();
    }

    public List<ResponseSource> responseSources(UUID attemptId) {
        return jdbc.query("""
                select question_key, answer::text from public.test_attempt_responses
                where attempt_id=? and question_type='SPEAKING_QUESTION' order by answered_at, question_key
                """, (rs, ignored) -> {
            try {
                Map<String, Object> answer = objectMapper.readValue(rs.getString("answer"), new TypeReference<>() {});
                return new ResponseSource(rs.getString("question_key"), text(answer.get("transcript")),
                        text(answer.get("audioUrl")));
            } catch (Exception exception) {
                return new ResponseSource(rs.getString("question_key"), null, null);
            }
        }, attemptId);
    }

    private String text(Object value) { return value == null || value.toString().isBlank() ? null : value.toString(); }
    public record ResponseSource(String questionKey, String transcript, String audioUrl) {}
    private record Entitlement(boolean unlimited, Integer monthlyLimit) {}
}
