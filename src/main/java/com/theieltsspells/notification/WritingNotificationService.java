package com.theieltsspells.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
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
                  and not exists(select 1 from public.notifications notice
                    where notice.type=? and notice.data->>'evaluationId'=cast(evaluation.id as text))
                """, teacher ? "WRITING_TEACHER" : "WRITING_TIS",
                teacher ? "Giáo viên đã công bố bài chấm Writing" : "TIS đã chấm xong bài Writing",
                "Điểm và nhận xét của bạn đã sẵn sàng.",
                evaluationId, teacher ? "WRITING_TEACHER" : "WRITING_TIS");
    }

    @Transactional(readOnly = true)
    public List<Notice> unread(UUID studentId) {
        return jdbc.query("""
                select id, title, body, data->>'attemptId' attempt_id
                from public.notifications where user_id=? and read_at is null
                  and type in ('WRITING_TIS', 'WRITING_TEACHER')
                order by created_at limit 20
                """, (rs, row) -> new Notice(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getString("body"), rs.getString("attempt_id")), studentId);
    }

    @Transactional
    public void markRead(UUID id, UUID studentId) {
        jdbc.update("update public.notifications set read_at=now() where id=? and user_id=? and read_at is null", id, studentId);
    }

    public record Notice(UUID id, String title, String body, String attemptId) {}
}
