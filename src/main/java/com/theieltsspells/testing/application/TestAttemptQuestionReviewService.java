package com.theieltsspells.testing.application;

import com.theieltsspells.academic.application.AcademicMembershipService;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.testing.application.dto.SaveTestAttemptQuestionReviewRequest;
import com.theieltsspells.testing.application.dto.TestAttemptQuestionReviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestAttemptQuestionReviewService {

    private final JdbcTemplate jdbc;
    private final AcademicMembershipService memberships;

    public List<TestAttemptQuestionReviewResponse> listForStudent(UUID attemptId, UUID studentId) {
        assertStudentAttempt(attemptId, studentId);
        return listForAttempt(attemptId);
    }

    public List<TestAttemptQuestionReviewResponse> listForAttempt(UUID attemptId) {
        return jdbc.query("""
                select attempt_id, question_key, reason_code, note, needs_retry,
                  vocabulary_term, vocabulary_meaning, reviewed_at, updated_at
                from public.test_attempt_question_reviews
                where attempt_id = ?
                order by question_key
                """, this::mapReview, attemptId);
    }

    @Transactional
    public TestAttemptQuestionReviewResponse save(UUID attemptId, String questionKey,
                                                   SaveTestAttemptQuestionReviewRequest request,
                                                   UUID studentId) {
        var context = assertStudentAttempt(attemptId, studentId);
        assertReviewable(context);
        assertQuestionExists(context.testVersionId(), questionKey);
        assertQuestionNeedsReview(attemptId, questionKey);
        return jdbc.queryForObject("""
                insert into public.test_attempt_question_reviews(
                  attempt_id, question_key, reason_code, note, needs_retry,
                  vocabulary_term, vocabulary_meaning, reviewed_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, now(), now())
                on conflict (attempt_id, question_key) do update set
                  reason_code = excluded.reason_code,
                  note = excluded.note,
                  needs_retry = excluded.needs_retry,
                  vocabulary_term = excluded.vocabulary_term,
                  vocabulary_meaning = excluded.vocabulary_meaning,
                  updated_at = now()
                returning attempt_id, question_key, reason_code, note, needs_retry,
                  vocabulary_term, vocabulary_meaning, reviewed_at, updated_at
                """, this::mapReview, attemptId, questionKey, request.reasonCode(), request.note(),
                request.needsRetry(), request.vocabularyTerm(), request.vocabularyMeaning());
    }

    @Transactional
    public void delete(UUID attemptId, String questionKey, UUID studentId) {
        var context = assertStudentAttempt(attemptId, studentId);
        assertReviewable(context);
        jdbc.update("delete from public.test_attempt_question_reviews where attempt_id = ? and question_key = ?",
                attemptId, questionKey);
    }

    private AttemptContext assertStudentAttempt(UUID attemptId, UUID studentId) {
        var contexts = jdbc.query("""
                select attempt.student_id, attempt.status::text attempt_status,
                  attempt.test_version_id, assignment.course_id
                from public.test_attempts attempt
                left join public.test_assignments assignment on assignment.id = attempt.test_assignment_id
                where attempt.id = ?
                """, (rs, ignored) -> new AttemptContext(
                rs.getObject("student_id", UUID.class), rs.getString("attempt_status"),
                rs.getObject("test_version_id", UUID.class), rs.getObject("course_id", UUID.class)
        ), attemptId);
        if (contexts.isEmpty() || !studentId.equals(contexts.getFirst().studentId())) {
            throw new ResourceNotFoundException("Không tìm thấy lượt làm bài");
        }
        var context = contexts.getFirst();
        if (context.courseId() != null && !memberships.hasActiveEnrollment(context.courseId(), studentId)) {
            throw new ResourceNotFoundException("Không tìm thấy lượt làm bài");
        }
        return context;
    }

    private void assertReviewable(AttemptContext context) {
        if ("IN_PROGRESS".equals(context.status())) {
            throw new BusinessRuleException("Chỉ có thể review sau khi đã nộp bài");
        }
    }

    private void assertQuestionExists(UUID testVersionId, String questionKey) {
        Integer count = jdbc.queryForObject("""
                select count(*) from public.test_version_questions
                where test_version_id = ? and question_key = ?
                """, Integer.class, testVersionId, questionKey);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Không tìm thấy câu hỏi trong phiên bản đề");
        }
    }

    private void assertQuestionNeedsReview(UUID attemptId, String questionKey) {
        var values = jdbc.query("""
                select is_correct from public.test_attempt_responses
                where attempt_id = ? and question_key = ?
                """, (rs, ignored) -> (Boolean) rs.getObject("is_correct"), attemptId, questionKey);
        if (!values.isEmpty() && Boolean.TRUE.equals(values.getFirst())) {
            throw new BusinessRuleException("Chỉ có thể review câu chưa đúng hoặc bỏ trống");
        }
    }

    private TestAttemptQuestionReviewResponse mapReview(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        return new TestAttemptQuestionReviewResponse(
                rs.getObject("attempt_id", UUID.class), rs.getString("question_key"),
                rs.getString("reason_code"), rs.getString("note"), rs.getBoolean("needs_retry"),
                rs.getString("vocabulary_term"), rs.getString("vocabulary_meaning"),
                rs.getObject("reviewed_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private record AttemptContext(UUID studentId, String status, UUID testVersionId, UUID courseId) {
    }
}
