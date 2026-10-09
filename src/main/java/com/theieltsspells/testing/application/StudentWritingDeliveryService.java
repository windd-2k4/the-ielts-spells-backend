package com.theieltsspells.testing.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.academic.application.AcademicMembershipService;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.testing.application.dto.SaveWritingResponsesRequest;
import com.theieltsspells.testing.application.dto.StudentWritingAssignmentResponse;
import com.theieltsspells.testing.application.dto.StudentWritingAttemptResponse;
import com.theieltsspells.testing.application.dto.WritingAttemptResultResponse;
import com.theieltsspells.testing.application.dto.WritingSavedResponse;
import com.theieltsspells.testing.application.dto.WritingTaskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Student Writing delivery pinned to the immutable published test version. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentWritingDeliveryService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AcademicMembershipService memberships;

    public List<StudentWritingAssignmentResponse> listAssignments(UUID studentId) {
        return jdbc.query("""
                select assignment.id assignment_id, assignment.test_version_id, version.title,
                  version.version_label, assignment.course_id, course.name course_name,
                  assignment.mode, assignment.opens_at, assignment.closes_at,
                  assignment.max_attempts, assignment.duration_seconds,
                  (select count(*) from public.test_attempts attempt
                    where attempt.test_assignment_id = assignment.id and attempt.student_id = ?) attempts_used,
                  (select max(attempt.expires_at) from public.test_attempts attempt
                    where attempt.test_assignment_id = assignment.id and attempt.student_id = ?
                      and attempt.status = 'IN_PROGRESS') active_attempt_expires_at
                from public.test_assignments assignment
                join public.test_versions version on version.id = assignment.test_version_id
                join public.courses course on course.id = assignment.course_id
                join public.enrollments enrollment on enrollment.course_id = assignment.course_id
                where enrollment.student_id = ? and enrollment.status = 'ACTIVE'
                  and version.primary_skill = 'WRITING' and assignment.archived_at is null
                order by coalesce(assignment.opens_at, assignment.created_at) desc
                """, (rs, ignored) -> new StudentWritingAssignmentResponse(
                rs.getObject("assignment_id", UUID.class), rs.getObject("test_version_id", UUID.class),
                rs.getString("title"), rs.getString("version_label"), rs.getObject("course_id", UUID.class),
                rs.getString("course_name"), rs.getString("mode"),
                rs.getObject("opens_at", OffsetDateTime.class), rs.getObject("closes_at", OffsetDateTime.class),
                rs.getShort("max_attempts"), rs.getInt("attempts_used"),
                rs.getObject("duration_seconds", Integer.class),
                rs.getObject("active_attempt_expires_at", OffsetDateTime.class)
        ), studentId, studentId, studentId);
    }

    @Transactional
    public StudentWritingAttemptResponse startOrResume(UUID assignmentId, UUID studentId) {
        var assignment = loadAssignment(assignmentId, true);
        assertStudentMayAccess(assignment, studentId);
        var now = now();
        assertWithinWindow(assignment, now);
        var active = activeAttempt("test_assignment_id = ?", assignmentId, studentId);
        if (active != null) return payload(loadAttempt(active, studentId, false));

        Integer used = jdbc.queryForObject("select count(*) from public.test_attempts where test_assignment_id=? and student_id=?",
                Integer.class, assignmentId, studentId);
        if (used != null && used >= assignment.maxAttempts()) {
            throw new BusinessRuleException("Bạn đã dùng hết số lượt làm bài được phép");
        }
        var expiresAt = expiresAt(assignment.durationSeconds(), assignment.durationMinutes(), assignment.closesAt(), now);
        UUID attemptId = jdbc.queryForObject("""
                insert into public.test_attempts(test_assignment_id, test_version_id, student_id, attempt_no,
                  status, started_at, expires_at, last_saved_at, attempt_origin)
                values (?, ?, ?, ?, 'IN_PROGRESS', ?, ?, ?, 'ASSIGNMENT') returning id
                """, UUID.class, assignment.id(), assignment.testVersionId(), studentId,
                (short) ((used == null ? 0 : used) + 1), now, expiresAt, now);
        return payload(loadAttempt(attemptId, studentId, false));
    }

    @Transactional
    public StudentWritingAttemptResponse startOrResumeSelfPractice(UUID testVersionId, UUID studentId) {
        return startOrResumeSelfPractice(testVersionId, studentId, false);
    }

    @Transactional
    public StudentWritingAttemptResponse startOrResumeSelfPractice(UUID testVersionId, UUID studentId,
                                                                   boolean restart) {
        var versions = jdbc.query("""
                select version.id, version.duration_minutes, version.primary_skill
                from public.tests test join public.test_versions version on version.id=test.current_published_version_id
                where version.id=? and test.status='PUBLISHED' for update of test
                """, (rs, ignored) -> new VersionContext(rs.getObject("id", UUID.class),
                rs.getInt("duration_minutes"), rs.getString("primary_skill")), testVersionId);
        if (versions.isEmpty() || !"WRITING".equals(versions.getFirst().skill())) {
            throw new ResourceNotFoundException("Không tìm thấy đề Writing đã xuất bản");
        }
        var active = activeAttempt("test_version_id = ? and attempt_origin = 'SELF_PRACTICE'", testVersionId, studentId);
        if (active != null) {
            if (!restart) return payload(loadAttempt(active, studentId, false));
            finalizeAttempt(loadAttempt(active, studentId, true), "EXPIRED");
        }
        Integer used = jdbc.queryForObject("""
                select count(*) from public.test_attempts where test_version_id=? and student_id=?
                  and attempt_origin='SELF_PRACTICE'
                """, Integer.class, testVersionId, studentId);
        var now = now();
        var expiresAt = now.plusMinutes(Math.max(1, versions.getFirst().durationMinutes()));
        UUID attemptId = jdbc.queryForObject("""
                insert into public.test_attempts(test_assignment_id, test_version_id, student_id, attempt_no,
                  status, started_at, expires_at, last_saved_at, attempt_origin)
                values (null, ?, ?, ?, 'IN_PROGRESS', ?, ?, ?, 'SELF_PRACTICE') returning id
                """, UUID.class, testVersionId, studentId,
                (short) Math.min(Short.MAX_VALUE, (used == null ? 0 : used) + 1), now, expiresAt, now);
        return payload(loadAttempt(attemptId, studentId, false));
    }

    public StudentWritingAttemptResponse getAttempt(UUID attemptId, UUID studentId) {
        return payload(loadAttempt(attemptId, studentId, false));
    }

    @Transactional
    public StudentWritingAttemptResponse resumeAttempt(UUID attemptId, UUID studentId) {
        var attempt = loadAttempt(attemptId, studentId, true);
        assertInProgress(attempt);
        var pausedRemaining = pausedRemainingSeconds(attempt);
        if ("SELF_PRACTICE".equals(attempt.origin()) && pausedRemaining != null) {
            if (pausedRemaining <= 0) {
                finalizeAttempt(attempt, "EXPIRED");
            } else {
                jdbc.update("""
                        update public.test_attempts
                        set expires_at = now() + (? * interval '1 second'),
                          paused_remaining_seconds = null, updated_at = now()
                        where id = ? and status = 'IN_PROGRESS'
                        """, pausedRemaining, attempt.id());
            }
        }
        return payload(loadAttempt(attemptId, studentId, false));
    }

    @Transactional
    public void pauseAttempt(UUID attemptId, UUID studentId) {
        var attempt = loadAttempt(attemptId, studentId, true);
        if (!"SELF_PRACTICE".equals(attempt.origin()) || !"IN_PROGRESS".equals(attempt.status())
                || pausedRemainingSeconds(attempt) != null) return;
        jdbc.update("""
                update public.test_attempts
                set paused_remaining_seconds = ?,
                  updated_at = now()
                where id = ? and status = 'IN_PROGRESS'
                """, remainingSeconds(attempt.expiresAt()), attempt.id());
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public StudentWritingAttemptResponse saveResponses(UUID attemptId, SaveWritingResponsesRequest request,
                                                       UUID studentId) {
        var attempt = loadAttempt(attemptId, studentId, true);
        assertInProgress(attempt);
        if (isExpired(attempt)) {
            finalizeAttempt(attempt, "EXPIRED");
            throw new BusinessRuleException("Đã hết thời gian làm bài; bài viết đã lưu được gửi để chấm");
        }
        var taskKeys = new HashSet<String>();
        tasks(attempt.builderContent()).forEach(task -> taskKeys.add(task.taskKey()));
        var seen = new HashSet<String>();
        for (var response : request.responses()) {
            if (!seen.add(response.taskKey())) {
                throw new BusinessRuleException("Một Writing Task chỉ được lưu một lần trong mỗi yêu cầu");
            }
            if (!taskKeys.contains(response.taskKey())) {
                throw new BusinessRuleException("Writing Task không thuộc phiên bản đề này: " + response.taskKey());
            }
            String answer = json(Map.of("text", response.text()));
            jdbc.update("""
                    insert into public.test_attempt_responses(attempt_id, question_key, question_type, answer,
                      normalized_answer, max_score, client_revision, answered_at, updated_at)
                    values (?, ?, 'WRITING_TASK', cast(? as jsonb), '[]'::jsonb, 9, ?, now(), now())
                    on conflict (attempt_id, question_key) do update set answer=excluded.answer,
                      client_revision=excluded.client_revision, answered_at=now(), updated_at=now()
                    where public.test_attempt_responses.client_revision <= excluded.client_revision
                    """, attempt.id(), response.taskKey(), answer, response.clientRevision());
        }
        jdbc.update("update public.test_attempts set last_saved_at=now(), updated_at=now() where id=?", attempt.id());
        return payload(loadAttempt(attempt.id(), studentId, false));
    }

    @Transactional
    public WritingAttemptResultResponse submit(UUID attemptId, UUID studentId) {
        var attempt = loadAttempt(attemptId, studentId, true);
        if ("IN_PROGRESS".equals(attempt.status())) {
            finalizeAttempt(attempt, isExpired(attempt) ? "EXPIRED" : "SUBMITTED");
        }
        return result(loadAttempt(attemptId, studentId, false));
    }

    public WritingAttemptResultResponse getResult(UUID attemptId, UUID studentId) {
        var attempt = loadAttempt(attemptId, studentId, false);
        if ("IN_PROGRESS".equals(attempt.status())) {
            throw new ConflictException("Bài Writing chưa được nộp");
        }
        return result(attempt);
    }

    private void finalizeAttempt(AttemptContext attempt, String status) {
        jdbc.update("""
                update public.test_attempts set status=cast(? as public.attempt_status), submitted_at=coalesce(submitted_at, now()),
                  last_saved_at=now(), updated_at=now() where id=? and status='IN_PROGRESS'
                """, status, attempt.id());
        Map<String, WritingTaskResponse> tasks = new LinkedHashMap<>();
        tasks(attempt.builderContent()).forEach(task -> tasks.put(task.taskKey(), task));
        var submittedResponses = jdbc.query("""
                select id, question_key, answer::text from public.test_attempt_responses
                where attempt_id=? and question_type='WRITING_TASK'
                """, (rs, ignored) -> new SubmittedResponse(rs.getObject("id", UUID.class),
                rs.getString("question_key"), answerText(rs.getString("answer"))), attempt.id());
        for (var response : submittedResponses) {
            var task = tasks.get(response.taskKey());
            String text = response.text();
            if (task != null && !text.isBlank()) {
                jdbc.update("""
                        insert into public.writing_evaluations(test_attempt_response_id, task_type, essay_text,
                          status, rubric_version, prompt_version)
                        values (?, ?, ?, 'QUEUED', 'ielts-writing-v1', 'writing-evaluator-v1')
                        on conflict (test_attempt_response_id) where test_attempt_response_id is not null do nothing
                        """, response.id(), task.taskNo() == 1 ? "academic_task_1" : "task_2", text);
            }
        }
    }

    private StudentWritingAttemptResponse payload(AttemptContext attempt) {
        return new StudentWritingAttemptResponse(attempt.id(), attempt.assignmentId(), attempt.testVersionId(),
                attempt.status(), attempt.startedAt(), attempt.expiresAt(), effectiveRemainingSeconds(attempt),
                attempt.title(), attempt.description(), attempt.showResultAfterSubmit(),
                tasks(attempt.builderContent()), responses(attempt.id()));
    }

    private WritingAttemptResultResponse result(AttemptContext attempt) {
        boolean visible = attempt.showResultAfterSubmit();
        return new WritingAttemptResultResponse(attempt.id(), attempt.status(), attempt.submittedAt(), visible,
                tasks(attempt.builderContent()), responses(attempt.id()), visible ? publishedEvaluations(attempt.id()) : List.of());
    }

    private List<WritingAttemptResultResponse.Evaluation> publishedEvaluations(UUID attemptId) {
        return jdbc.query("""
                select response.question_key, evaluation.id, evaluation.status::text status,
                  coalesce(evaluation.overall_band_teacher, evaluation.overall_band_ai) overall_band,
                  evaluation.strengths::text strengths, evaluation.improvements::text improvements,
                  evaluation.published_at
                from public.writing_evaluations evaluation
                join public.test_attempt_responses response on response.id=evaluation.test_attempt_response_id
                where response.attempt_id=? and evaluation.status='PUBLISHED'
                order by response.question_key
                """, (rs, ignored) -> new WritingAttemptResultResponse.Evaluation(
                rs.getString("question_key"), rs.getString("status"), rs.getBigDecimal("overall_band"),
                criterionBands(rs.getObject("id", UUID.class)), strings(rs.getString("strengths")),
                strings(rs.getString("improvements")), rs.getObject("published_at", OffsetDateTime.class)
        ), attemptId);
    }

    private Map<String, BigDecimal> criterionBands(UUID evaluationId) {
        var result = new LinkedHashMap<String, BigDecimal>();
        jdbc.query("""
                select criterion, coalesce(teacher_band, ai_band) band from public.writing_criterion_scores
                where evaluation_id=? order by criterion
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                result.put(rs.getString("criterion"), rs.getBigDecimal("band")), evaluationId);
        return result;
    }

    private List<WritingSavedResponse> responses(UUID attemptId) {
        return jdbc.query("""
                select question_key, answer::text, client_revision, answered_at
                from public.test_attempt_responses where attempt_id=? and question_type='WRITING_TASK'
                order by question_key
                """, (rs, ignored) -> {
            String text = answerText(rs.getString("answer"));
            return new WritingSavedResponse(rs.getString("question_key"), text, wordCount(text),
                    rs.getInt("client_revision"), rs.getObject("answered_at", OffsetDateTime.class));
        }, attemptId);
    }

    @SuppressWarnings("unchecked")
    private List<WritingTaskResponse> tasks(Map<String, Object> content) {
        Object source = content.containsKey("tasks") ? content.get("tasks") : content.get("writingTasks");
        if (!(source instanceof List<?> values)) return List.of();
        var tasks = new ArrayList<WritingTaskResponse>();
        int index = 0;
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> raw)) continue;
            var task = (Map<String, Object>) raw;
            int taskNo = number(task.get("taskNo"), index + 1);
            String key = text(task.get("id"), "writing-task-" + taskNo);
            tasks.add(new WritingTaskResponse(key, taskNo, text(task.get("title"), "Writing Task " + taskNo),
                    text(task.get("promptHtml"), ""), nullableText(task.get("imageUrl")),
                    nullableText(task.get("imageAltText")), number(task.get("minWords"), taskNo == 1 ? 150 : 250),
                    number(task.get("suggestedTimeMinutes"), taskNo == 1 ? 20 : 40),
                    text(task.get("responseMode"), "FREEFORM")));
            index++;
        }
        return List.copyOf(tasks);
    }

    private UUID activeAttempt(String predicate, UUID value, UUID studentId) {
        var ids = jdbc.query("select id from public.test_attempts where " + predicate +
                        " and student_id=? and status='IN_PROGRESS' order by started_at desc limit 1",
                (rs, ignored) -> rs.getObject("id", UUID.class), value, studentId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private AssignmentContext loadAssignment(UUID id, boolean lock) {
        var values = jdbc.query("""
                select assignment.id, assignment.test_version_id, assignment.course_id, assignment.opens_at,
                  assignment.closes_at, assignment.max_attempts, assignment.duration_seconds,
                  assignment.show_result_after_submit, assignment.archived_at, version.duration_minutes,
                  version.primary_skill from public.test_assignments assignment
                join public.test_versions version on version.id=assignment.test_version_id where assignment.id=?
                """ + (lock ? " for update of assignment" : ""), (rs, ignored) -> new AssignmentContext(
                rs.getObject("id", UUID.class), rs.getObject("test_version_id", UUID.class),
                rs.getObject("course_id", UUID.class), rs.getObject("opens_at", OffsetDateTime.class),
                rs.getObject("closes_at", OffsetDateTime.class), rs.getShort("max_attempts"),
                rs.getObject("duration_seconds", Integer.class), rs.getBoolean("show_result_after_submit"),
                rs.getObject("archived_at", OffsetDateTime.class), rs.getInt("duration_minutes"),
                rs.getString("primary_skill")), id);
        if (values.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy bài Writing được giao");
        if (!"WRITING".equals(values.getFirst().skill())) throw new BusinessRuleException("Bài được giao không phải đề Writing");
        return values.getFirst();
    }

    private AttemptContext loadAttempt(UUID id, UUID studentId, boolean lock) {
        var values = jdbc.query("""
                select attempt.id, attempt.test_assignment_id, attempt.test_version_id, attempt.student_id,
                  attempt.attempt_origin, attempt.status::text status, attempt.started_at, attempt.submitted_at,
                  attempt.expires_at, attempt.paused_remaining_seconds, assignment.course_id, coalesce(assignment.show_result_after_submit, true) show_result,
                  version.title, version.description, version.primary_skill, version.builder_content::text builder_content
                from public.test_attempts attempt
                left join public.test_assignments assignment on assignment.id=attempt.test_assignment_id
                join public.test_versions version on version.id=attempt.test_version_id where attempt.id=?
                """ + (lock ? " for update of attempt" : ""), (rs, ignored) -> new AttemptContext(
                rs.getObject("id", UUID.class), rs.getObject("test_assignment_id", UUID.class),
                rs.getObject("test_version_id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getString("attempt_origin"), rs.getString("status"), rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("submitted_at", OffsetDateTime.class), rs.getObject("expires_at", OffsetDateTime.class),
                rs.getObject("paused_remaining_seconds", Long.class),
                rs.getObject("course_id", UUID.class), rs.getBoolean("show_result"), rs.getString("title"),
                rs.getString("description"), rs.getString("primary_skill"), readMap(rs.getString("builder_content"))), id);
        if (values.isEmpty() || !values.getFirst().studentId().equals(studentId)) {
            throw new ResourceNotFoundException("Không tìm thấy lượt làm bài Writing");
        }
        var attempt = values.getFirst();
        if (!"WRITING".equals(attempt.skill())) throw new BusinessRuleException("Lượt làm này không phải đề Writing");
        if ("ASSIGNMENT".equals(attempt.origin()) && !memberships.hasActiveEnrollment(attempt.courseId(), studentId)) {
            throw new BusinessRuleException("Bạn không có ghi danh đang hoạt động trong khóa học này");
        }
        return attempt;
    }

    private void assertStudentMayAccess(AssignmentContext assignment, UUID studentId) {
        if (assignment.archivedAt() != null) throw new ResourceNotFoundException("Bài kiểm tra không còn khả dụng");
        if (!memberships.hasActiveEnrollment(assignment.courseId(), studentId)) {
            throw new BusinessRuleException("Bạn không có ghi danh đang hoạt động trong khóa học này");
        }
    }

    private void assertWithinWindow(AssignmentContext assignment, OffsetDateTime now) {
        if (assignment.opensAt() != null && now.isBefore(assignment.opensAt())) throw new BusinessRuleException("Bài kiểm tra chưa đến thời gian mở");
        if (assignment.closesAt() != null && !now.isBefore(assignment.closesAt())) throw new BusinessRuleException("Bài kiểm tra đã đóng");
    }

    private void assertInProgress(AttemptContext attempt) {
        if (!"IN_PROGRESS".equals(attempt.status())) throw new ConflictException("Bài Writing không còn ở trạng thái đang làm");
    }

    private OffsetDateTime expiresAt(Integer seconds, int minutes, OffsetDateTime closesAt, OffsetDateTime startedAt) {
        var value = startedAt.plusSeconds(seconds == null ? Math.max(1, minutes) * 60L : seconds);
        return closesAt != null && closesAt.isBefore(value) ? closesAt : value;
    }

    private long remainingSeconds(OffsetDateTime expiresAt) {
        return expiresAt == null ? 0 : Math.max(0, expiresAt.toEpochSecond() - now().toEpochSecond());
    }

    private Long pausedRemainingSeconds(AttemptContext attempt) {
        return attempt.pausedRemainingSeconds();
    }

    private long effectiveRemainingSeconds(AttemptContext attempt) {
        var paused = pausedRemainingSeconds(attempt);
        return paused == null ? remainingSeconds(attempt.expiresAt()) : paused;
    }

    private boolean isExpired(AttemptContext attempt) {
        return pausedRemainingSeconds(attempt) == null && !now().isBefore(attempt.expiresAt());
    }

    private int wordCount(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }

    private String answerText(String json) {
        try {
            Object value = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}).get("text");
            return value instanceof String text ? text : "";
        } catch (Exception exception) {
            throw new BusinessRuleException("Không thể đọc bài Writing đã lưu");
        }
    }

    private Map<String, Object> readMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new BusinessRuleException("Nội dung phiên bản Writing không hợp lệ");
        }
    }

    private List<String> strings(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessRuleException("Không thể lưu bài Writing");
        }
    }

    private int number(Object value, int fallback) { return value instanceof Number number ? number.intValue() : fallback; }
    private String text(Object value, String fallback) { return value instanceof String text && !text.isBlank() ? text : fallback; }
    private String nullableText(Object value) { return value instanceof String text && !text.isBlank() ? text : null; }
    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }

    private record VersionContext(UUID id, int durationMinutes, String skill) { }
    private record SubmittedResponse(UUID id, String taskKey, String text) { }
    private record AssignmentContext(UUID id, UUID testVersionId, UUID courseId, OffsetDateTime opensAt,
                                     OffsetDateTime closesAt, short maxAttempts, Integer durationSeconds,
                                     boolean showResultAfterSubmit, OffsetDateTime archivedAt,
                                     int durationMinutes, String skill) { }
    private record AttemptContext(UUID id, UUID assignmentId, UUID testVersionId, UUID studentId, String origin,
                                  String status, OffsetDateTime startedAt, OffsetDateTime submittedAt,
                                  OffsetDateTime expiresAt, Long pausedRemainingSeconds,
                                  UUID courseId, boolean showResultAfterSubmit,
                                  String title, String description, String skill, Map<String, Object> builderContent) { }
}
