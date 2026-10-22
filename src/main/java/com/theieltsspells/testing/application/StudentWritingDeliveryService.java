package com.theieltsspells.testing.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.academic.application.AcademicMembershipService;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.application.WritingSubmittedEvent;
import com.theieltsspells.shared.WritingBandScorePolicy;
import com.theieltsspells.testing.application.dto.SaveWritingResponsesRequest;
import com.theieltsspells.testing.application.dto.StudentWritingAssignmentResponse;
import com.theieltsspells.testing.application.dto.StudentWritingAttemptResponse;
import com.theieltsspells.testing.application.dto.WritingAttemptResultResponse;
import com.theieltsspells.testing.application.dto.WritingSavedResponse;
import com.theieltsspells.testing.application.dto.WritingTaskResponse;
import com.theieltsspells.writingevaluation.application.dto.WritingTeacherAnnotation;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;

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
                  status, started_at, expires_at, last_saved_at, attempt_origin, paused_remaining_seconds)
                values (null, ?, ?, ?, 'IN_PROGRESS', ?, ?, ?, 'SELF_PRACTICE', ?) returning id
                """, UUID.class, testVersionId, studentId,
                (short) Math.min(Short.MAX_VALUE, (used == null ? 0 : used) + 1), now, expiresAt, now,
                Math.max(1, versions.getFirst().durationMinutes()) * 60L);
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
        if ("SELF_PRACTICE".equals(attempt.origin())
                && (pausedRemaining != null || remainingSeconds(attempt.expiresAt()) == 0)) {
            long resumeBudget = pausedRemaining != null && pausedRemaining > 0
                    ? pausedRemaining : fullDurationSeconds(attempt);
            jdbc.update("""
                    update public.test_attempts
                    set expires_at = now() + (? * interval '1 second'),
                      paused_remaining_seconds = null, updated_at = now()
                    where id = ? and status = 'IN_PROGRESS'
                    """, resumeBudget, attempt.id());
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
                """, pauseBudgetSeconds(attempt), attempt.id());
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
                Map<String, Object> taskSource = taskSource(attempt.builderContent(), response.taskKey());
                String type = task.taskNo() == 2 ? "task_2"
                        : "GENERAL".equalsIgnoreCase(nullableText(taskSource.get("ieltsFormat")))
                        ? "general_task_1" : "academic_task_1";
                var context = new LinkedHashMap<String, Object>();
                context.put("promptHtml", task.promptHtml());
                context.put("imageUrl", task.imageUrl() == null ? "" : task.imageUrl());
                context.put("imageAltText", task.imageAltText() == null ? "" : task.imageAltText());
                if (nullableText(taskSource.get("imageAssetId")) != null) {
                    context.put("imageAssetId", nullableText(taskSource.get("imageAssetId")));
                }
                context.put("taskNo", task.taskNo());
                context.put("minWords", task.minWords());
                context.put("testVersionId", attempt.testVersionId().toString());
                context.put("taskKey", task.taskKey());
                events.publishEvent(new WritingSubmittedEvent(response.id(), type, text, context));
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
        List<WritingTaskResponse> tasks = tasks(attempt.builderContent(), visible);
        List<WritingAttemptResultResponse.Evaluation> evaluations = evaluations(attempt.id());
        if (!visible) evaluations = evaluations.stream().map(value -> new WritingAttemptResultResponse.Evaluation(
                value.taskKey(), value.aiStatus(), null, Map.of(), List.of(), List.of(), null,
                value.teacherStatus(), null, Map.of(), List.of(), List.of(), null, null, List.of())).toList();
        int requestable = (int) evaluations.stream().filter(value -> "NOT_ELIGIBLE".equals(value.aiStatus())).count();
        return new WritingAttemptResultResponse(attempt.id(), attempt.status(), attempt.submittedAt(), visible,
                attempt.origin(), visible && requestable > 0 && hasAiBudget(attempt.studentId(), requestable),
                fullBand(tasks, evaluations, true), fullBand(tasks, evaluations, false),
                tasks, responses(attempt.id()), evaluations);
    }

    private BigDecimal fullBand(List<WritingTaskResponse> tasks,
                                List<WritingAttemptResultResponse.Evaluation> evaluations,
                                boolean ai) {
        if (tasks.size() != 2) return null;
        var byTask = new java.util.HashMap<String, WritingAttemptResultResponse.Evaluation>();
        evaluations.forEach(value -> byTask.put(value.taskKey(), value));
        WritingAttemptResultResponse.Evaluation taskOne = null;
        WritingAttemptResultResponse.Evaluation taskTwo = null;
        for (var task : tasks) {
            if (task.taskNo() == 1) taskOne = byTask.get(task.taskKey());
            if (task.taskNo() == 2) taskTwo = byTask.get(task.taskKey());
        }
        if (taskOne == null || taskTwo == null) return null;
        if (!ai && (!"PUBLISHED".equals(taskOne.teacherStatus()) || !"PUBLISHED".equals(taskTwo.teacherStatus()))) {
            return null;
        }
        var first = ai ? taskOne.aiCriterionBands() : taskOne.teacherCriterionBands();
        var second = ai ? taskTwo.aiCriterionBands() : taskTwo.teacherCriterionBands();
        if (first.size() != 4 || second.size() != 4) return null;
        return WritingBandScorePolicy.fullWritingBand(first.values(), second.values());
    }

    private List<WritingAttemptResultResponse.Evaluation> evaluations(UUID attemptId) {
        return jdbc.query("""
                select response.question_key, evaluation.id, evaluation.status::text ai_status,
                  evaluation.overall_band_ai, evaluation.ai_strengths::text ai_strengths,
                  evaluation.ai_improvements::text ai_improvements, evaluation.ai_completed_at,
                  evaluation.teacher_status,
                  case when evaluation.teacher_status='PUBLISHED' then evaluation.overall_band_teacher end overall_band_teacher,
                  case when evaluation.teacher_status='PUBLISHED' then evaluation.strengths else '[]'::jsonb end::text teacher_strengths,
                  case when evaluation.teacher_status='PUBLISHED' then evaluation.improvements else '[]'::jsonb end::text teacher_improvements,
                  case when evaluation.teacher_status='PUBLISHED' then evaluation.published_at end published_at,
                  case when evaluation.teacher_status='PUBLISHED' then coalesce(evaluation.teacher_published_text, evaluation.teacher_edited_text) end teacher_edited_text,
                  case when evaluation.teacher_status='PUBLISHED' then coalesce(evaluation.teacher_published_annotations, evaluation.teacher_annotations) else '[]'::jsonb end::text teacher_annotations
                from public.writing_evaluations evaluation
                join public.test_attempt_responses response on response.id=evaluation.test_attempt_response_id
                where response.attempt_id=?
                order by response.question_key
                """, (rs, ignored) -> new WritingAttemptResultResponse.Evaluation(
                rs.getString("question_key"), rs.getString("ai_status"), rs.getBigDecimal("overall_band_ai"),
                criterionBands(rs.getObject("id", UUID.class), "ai_band"), strings(rs.getString("ai_strengths")),
                strings(rs.getString("ai_improvements")), rs.getObject("ai_completed_at", OffsetDateTime.class),
                rs.getString("teacher_status"), rs.getBigDecimal("overall_band_teacher"),
                "PUBLISHED".equals(rs.getString("teacher_status"))
                        ? criterionBands(rs.getObject("id", UUID.class), "teacher_band") : Map.of(),
                "PUBLISHED".equals(rs.getString("teacher_status"))
                        ? strings(rs.getString("teacher_strengths")) : List.of(),
                "PUBLISHED".equals(rs.getString("teacher_status"))
                        ? strings(rs.getString("teacher_improvements")) : List.of(),
                rs.getObject("published_at", OffsetDateTime.class),
                rs.getString("teacher_edited_text"),
                "PUBLISHED".equals(rs.getString("teacher_status")) ? annotations(rs.getString("teacher_annotations")) : List.of()
        ), attemptId);
    }

    private boolean hasAiBudget(UUID studentId, int requestedCount) {
        var entitlements = jdbc.query("""
                select unlimited, monthly_limit from public.writing_ai_entitlements
                where student_id=? and enabled and active_from <= now()
                  and (expires_at is null or expires_at > now())
                """, (rs, ignored) -> new AiBudget(rs.getBoolean("unlimited"),
                rs.getObject("monthly_limit", Integer.class)), studentId);
        if (entitlements.isEmpty()) return false;
        if (entitlements.getFirst().unlimited()) return true;
        Long used = jdbc.queryForObject("""
                select count(*) from public.writing_evaluations evaluation
                join public.test_attempt_responses response on response.id=evaluation.test_attempt_response_id
                join public.test_attempts attempt on attempt.id=response.attempt_id
                where attempt.student_id=?
                  and evaluation.ai_requested_at >= date_trunc('month', now())
                """, Long.class, studentId);
        Integer limit = entitlements.getFirst().monthlyLimit();
        return used != null && limit != null && used + requestedCount <= limit;
    }

    private Map<String, BigDecimal> criterionBands(UUID evaluationId, String column) {
        var result = new LinkedHashMap<String, BigDecimal>();
        jdbc.query("select criterion, " + column + " band from public.writing_criterion_scores " +
                "where evaluation_id=? and " + column + " is not null order by criterion",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
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
        return tasks(content, false);
    }

    @SuppressWarnings("unchecked")
    private List<WritingTaskResponse> tasks(Map<String, Object> content, boolean includeSample) {
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
                    text(task.get("responseMode"), "FREEFORM"), includeSample
                        ? com.theieltsspells.shared.util.HtmlSanitizer.sanitize(nullableText(task.get("sampleBand8Answer"))) : null));
            index++;
        }
        return List.copyOf(tasks);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> taskSource(Map<String, Object> content, String taskKey) {
        Object source = content.containsKey("tasks") ? content.get("tasks") : content.get("writingTasks");
        if (!(source instanceof List<?> values)) return Map.of();
        int index = 0;
        for (Object value : values) {
            if (value instanceof Map<?, ?> raw) {
                var task = (Map<String, Object>) raw;
                int taskNo = number(task.get("taskNo"), index + 1);
                if (taskKey.equals(text(task.get("id"), "writing-task-" + taskNo))) return task;
            }
            index++;
        }
        return Map.of();
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
                  version.title, version.description, version.duration_minutes, version.primary_skill,
                  version.builder_content::text builder_content
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
                rs.getString("description"), rs.getInt("duration_minutes"), rs.getString("primary_skill"),
                readMap(rs.getString("builder_content"))), id);
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

    private long pauseBudgetSeconds(AttemptContext attempt) {
        long remaining = remainingSeconds(attempt.expiresAt());
        return remaining > 0 ? remaining : fullDurationSeconds(attempt);
    }

    private long fullDurationSeconds(AttemptContext attempt) {
        return Math.max(1, attempt.durationMinutes()) * 60L;
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

    private List<WritingTeacherAnnotation> annotations(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception ignored) { return List.of(); }
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
    private record AiBudget(boolean unlimited, Integer monthlyLimit) { }
    private record SubmittedResponse(UUID id, String taskKey, String text) { }
    private record AssignmentContext(UUID id, UUID testVersionId, UUID courseId, OffsetDateTime opensAt,
                                     OffsetDateTime closesAt, short maxAttempts, Integer durationSeconds,
                                     boolean showResultAfterSubmit, OffsetDateTime archivedAt,
                                     int durationMinutes, String skill) { }
    private record AttemptContext(UUID id, UUID assignmentId, UUID testVersionId, UUID studentId, String origin,
                                  String status, OffsetDateTime startedAt, OffsetDateTime submittedAt,
                                  OffsetDateTime expiresAt, Long pausedRemainingSeconds,
                                  UUID courseId, boolean showResultAfterSubmit,
                                  String title, String description, int durationMinutes, String skill,
                                  Map<String, Object> builderContent) { }
}
