package com.theieltsspells.testing.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.academic.application.AcademicMembershipService;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.testing.application.dto.ReadingAttemptResultResponse;
import com.theieltsspells.testing.application.dto.SaveReadingResponseItem;
import com.theieltsspells.testing.application.dto.SaveReadingResponsesRequest;
import com.theieltsspells.testing.application.dto.StudentReadingAssignmentResponse;
import com.theieltsspells.testing.application.dto.StudentReadingAttemptResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Student-facing Reading attempt workflow backed only by immutable TestVersion rows. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentReadingDeliveryService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AcademicMembershipService memberships;
    private final ReadingAutoGrader grader = new ReadingAutoGrader();

    public List<StudentReadingAssignmentResponse> listAssignments(UUID studentId) {
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
                where enrollment.student_id = ?
                  and enrollment.status = 'ACTIVE'
                  and version.primary_skill = 'READING'
                  and assignment.archived_at is null
                order by coalesce(assignment.opens_at, assignment.created_at) desc
                """, (rs, ignored) -> new StudentReadingAssignmentResponse(
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
    public StudentReadingAttemptResponse startOrResume(UUID assignmentId, UUID studentId) {
        var assignment = loadAssignment(assignmentId, true);
        assertStudentMayAccess(assignment, studentId);
        var now = now();
        assertWithinAssignmentWindow(assignment, now);

        var active = jdbc.query("""
                select id from public.test_attempts
                where test_assignment_id = ? and student_id = ? and status = 'IN_PROGRESS'
                order by started_at desc limit 1
                """, (rs, ignored) -> rs.getObject("id", UUID.class), assignmentId, studentId);
        if (!active.isEmpty()) {
            return attemptPayload(loadAttempt(active.getFirst(), studentId, false));
        }

        Integer used = jdbc.queryForObject("""
                select count(*) from public.test_attempts where test_assignment_id = ? and student_id = ?
                """, Integer.class, assignmentId, studentId);
        if (used != null && used >= assignment.maxAttempts()) {
            throw new BusinessRuleException("Bạn đã dùng hết số lượt làm bài được phép");
        }

        OffsetDateTime expiresAt = expiresAt(assignment, now);
        if (!expiresAt.isAfter(now)) {
            throw new BusinessRuleException("Bài kiểm tra đã hết thời gian làm bài");
        }
        short attemptNo = (short) ((used == null ? 0 : used) + 1);
        UUID attemptId = jdbc.queryForObject("""
                insert into public.test_attempts(
                  test_assignment_id, test_version_id, student_id, attempt_no, status,
                  started_at, expires_at, last_saved_at
                ) values (?, ?, ?, ?, 'IN_PROGRESS', ?, ?, ?)
                returning id
                """, UUID.class, assignment.id(), assignment.testVersionId(), studentId,
                attemptNo, now, expiresAt, now);
        return attemptPayload(loadAttempt(attemptId, studentId, false));
    }

    public StudentReadingAttemptResponse getAttempt(UUID attemptId, UUID studentId) {
        return attemptPayload(loadAttempt(attemptId, studentId, false));
    }

    @Transactional
    public StudentReadingAttemptResponse saveResponses(UUID attemptId, SaveReadingResponsesRequest request,
                                                       UUID studentId) {
        var attempt = loadAttempt(attemptId, studentId, true);
        assertInProgress(attempt);
        if (!now().isBefore(attempt.expiresAt())) {
            finalizeAttempt(attempt, "EXPIRED");
            throw new BusinessRuleException("Đã hết thời gian làm bài; bài đã được chấm với các đáp án đã lưu");
        }

        var questions = definitions(attempt.testVersionId()).stream()
                .collect(Collectors.toMap(ReadingAutoGrader.QuestionDefinition::questionKey, value -> value));
        var seen = new java.util.HashSet<String>();
        for (var response : request.responses()) {
            if (!seen.add(response.questionKey())) {
                throw new BusinessRuleException("Một câu hỏi chỉ được lưu một lần trong mỗi yêu cầu");
            }
            var question = questions.get(response.questionKey());
            if (question == null) {
                throw new BusinessRuleException("Câu hỏi không thuộc phiên bản đề này: " + response.questionKey());
            }
            if (!response.answer().isObject()) {
                throw new BusinessRuleException("Đáp án phải có dạng đối tượng JSON, ví dụ {\"value\": \"A\"}");
            }
            var normalized = grader.normalizedValues(response.answer());
            jdbc.update("""
                    insert into public.test_attempt_responses(
                      attempt_id, question_key, question_type, answer, normalized_answer,
                      max_score, client_revision, answered_at, updated_at
                    ) values (?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, now(), now())
                    on conflict (attempt_id, question_key) do update set
                      answer = excluded.answer,
                      normalized_answer = excluded.normalized_answer,
                      is_correct = null,
                      auto_score = null,
                      max_score = excluded.max_score,
                      client_revision = excluded.client_revision,
                      answered_at = now(),
                      updated_at = now()
                    where public.test_attempt_responses.client_revision <= excluded.client_revision
                    """, attempt.id(), question.questionKey(), question.typeFormat(), json(response.answer()),
                    json(normalized), question.maxScore(), response.clientRevision());
        }
        jdbc.update("""
                update public.test_attempts set last_saved_at = now(), updated_at = now() where id = ?
                """, attempt.id());
        return attemptPayload(loadAttempt(attempt.id(), studentId, false));
    }

    @Transactional
    public ReadingAttemptResultResponse submit(UUID attemptId, UUID studentId) {
        var attempt = loadAttempt(attemptId, studentId, true);
        if ("IN_PROGRESS".equals(attempt.status())) {
            finalizeAttempt(attempt, now().isBefore(attempt.expiresAt()) ? "GRADED" : "EXPIRED");
        }
        return result(loadAttempt(attemptId, studentId, false));
    }

    public ReadingAttemptResultResponse getResult(UUID attemptId, UUID studentId) {
        var attempt = loadAttempt(attemptId, studentId, false);
        if ("IN_PROGRESS".equals(attempt.status())) {
            throw new ConflictException("Bài làm chưa được nộp");
        }
        return result(attempt);
    }

    private void finalizeAttempt(AttemptContext attempt, String finalStatus) {
        var definitions = definitions(attempt.testVersionId());
        var responses = storedResponses(attempt.id());
        var byKey = responses.stream().collect(Collectors.toMap(StoredResponse::questionKey, value -> value));
        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal maxScore = BigDecimal.ZERO;
        int correct = 0;
        int incorrect = 0;
        int unanswered = 0;

        for (var question : definitions) {
            maxScore = maxScore.add(question.maxScore());
            var response = byKey.get(question.questionKey());
            if (response == null || grader.normalizedValues(response.answer()).isEmpty()) {
                unanswered++;
                continue;
            }
            var evaluation = grader.evaluate(question, response.answer());
            totalScore = totalScore.add(evaluation.score());
            if (evaluation.correct()) {
                correct++;
            } else {
                incorrect++;
            }
            jdbc.update("""
                    update public.test_attempt_responses set normalized_answer = cast(? as jsonb),
                      is_correct = ?, auto_score = ?, max_score = ?, updated_at = now()
                    where id = ?
                    """, json(evaluation.normalizedAnswer()), evaluation.correct(), evaluation.score(),
                    question.maxScore(), response.id());
        }

        var metadata = Map.<String, Object>of("scoring", Map.of(
                "correctCount", correct,
                "incorrectCount", incorrect,
                "unansweredCount", unanswered,
                "maxScore", maxScore
        ));
        jdbc.update("""
                update public.test_attempts
                set status = cast(? as public.attempt_status), submitted_at = now(),
                  auto_score = ?, final_score = ?, metadata = cast(? as jsonb),
                  last_saved_at = now(), updated_at = now()
                where id = ?
                """, finalStatus, totalScore, totalScore, json(metadata), attempt.id());
    }

    private ReadingAttemptResultResponse result(AttemptContext attempt) {
        var definitions = definitions(attempt.testVersionId());
        var responses = storedResponses(attempt.id()).stream()
                .collect(Collectors.toMap(StoredResponse::questionKey, value -> value));
        BigDecimal maxScore = definitions.stream().map(ReadingAutoGrader.QuestionDefinition::maxScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int correct = 0;
        int incorrect = 0;
        int unanswered = 0;
        var questions = new ArrayList<ReadingAttemptResultResponse.QuestionResult>();
        for (var definition : definitions) {
            var response = responses.get(definition.questionKey());
            boolean answered = response != null && !grader.normalizedValues(response.answer()).isEmpty();
            if (!answered) {
                unanswered++;
            } else if (Boolean.TRUE.equals(response.correct())) {
                correct++;
            } else {
                incorrect++;
            }
            questions.add(new ReadingAttemptResultResponse.QuestionResult(
                    definition.questionKey(), definition.questionNo(), answered,
                    response == null ? null : response.correct(),
                    response == null ? BigDecimal.ZERO : response.autoScore(), definition.maxScore(),
                    attempt.showResultAfterSubmit() ? definition.correctAnswers() : List.of(),
                    attempt.showResultAfterSubmit() ? definition.explanation() : null
            ));
        }
        return new ReadingAttemptResultResponse(attempt.id(), attempt.status(), attempt.submittedAt(),
                attempt.expiresAt(), attempt.autoScore(), maxScore, correct, incorrect, unanswered,
                attempt.showResultAfterSubmit(), attempt.showResultAfterSubmit() ? questions : List.of());
    }

    private StudentReadingAttemptResponse attemptPayload(AttemptContext attempt) {
        return new StudentReadingAttemptResponse(
                attempt.id(), attempt.assignmentId(), attempt.testVersionId(), attempt.status(),
                attempt.startedAt(), attempt.expiresAt(), remainingSeconds(attempt.expiresAt()),
                attempt.title(), attempt.description(), attempt.showResultAfterSubmit(),
                studentSections(attempt.testVersionId()), savedResponseDtos(storedResponses(attempt.id())),
                attempt.autoScore(), attempt.finalScore()
        );
    }

    private List<Map<String, Object>> studentSections(UUID versionId) {
        var sections = jdbc.query("""
                select id, section_key, section_no, title, content_html
                from public.test_version_sections where test_version_id = ? order by display_order
                """, (rs, ignored) -> section(rs), versionId);
        var sectionById = sections.stream().collect(Collectors.toMap(
                value -> (UUID) value.get("_id"), value -> value, (left, right) -> left, LinkedHashMap::new));
        var groupsById = new LinkedHashMap<UUID, Map<String, Object>>();
        jdbc.query("""
                select id, section_id, group_key, title, type_format, instructions, answer_config::text answer_config
                from public.test_version_question_groups where test_version_id = ? order by display_order
                """, rs -> {
            UUID id = rs.getObject("id", UUID.class);
            var group = new LinkedHashMap<String, Object>();
            group.put("_id", id);
            group.put("key", rs.getString("group_key"));
            group.put("title", rs.getString("title"));
            group.put("typeFormat", rs.getString("type_format"));
            group.put("instructions", rs.getString("instructions"));
            group.put("answerConfig", readMap(rs.getString("answer_config")));
            group.put("sharedOptions", new ArrayList<Map<String, Object>>());
            group.put("questions", new ArrayList<Map<String, Object>>());
            groupsById.put(id, group);
            var section = sectionById.get(rs.getObject("section_id", UUID.class));
            if (section != null) {
                list(section, "questionGroups").add(group);
            }
        }, versionId);

        var questionsById = new LinkedHashMap<UUID, Map<String, Object>>();
        jdbc.query("""
                select id, group_id, question_key, question_no, type_format, prompt, max_score
                from public.test_version_questions where test_version_id = ? order by question_no
                """, rs -> {
            UUID id = rs.getObject("id", UUID.class);
            var question = new LinkedHashMap<String, Object>();
            question.put("_id", id);
            question.put("key", rs.getString("question_key"));
            question.put("number", rs.getInt("question_no"));
            question.put("typeFormat", rs.getString("type_format"));
            question.put("prompt", rs.getString("prompt"));
            question.put("maxScore", rs.getBigDecimal("max_score"));
            question.put("options", new ArrayList<Map<String, Object>>());
            questionsById.put(id, question);
            var group = groupsById.get(rs.getObject("group_id", UUID.class));
            if (group != null) {
                list(group, "questions").add(question);
            }
        }, versionId);

        jdbc.query("""
                select group_id, question_id, option_key, option_code, content
                from public.test_version_question_options where test_version_id = ? order by display_order
                """, rs -> {
            var option = new LinkedHashMap<String, Object>();
            option.put("key", rs.getString("option_key"));
            option.put("code", rs.getString("option_code"));
            option.put("text", rs.getString("content"));
            UUID questionId = rs.getObject("question_id", UUID.class);
            if (questionId == null) {
                var group = groupsById.get(rs.getObject("group_id", UUID.class));
                if (group != null) list(group, "sharedOptions").add(option);
            } else {
                var question = questionsById.get(questionId);
                if (question != null) list(question, "options").add(option);
            }
        }, versionId);

        sections.forEach(this::removeInternalId);
        return sections;
    }

    private Map<String, Object> section(java.sql.ResultSet rs) throws java.sql.SQLException {
        var result = new LinkedHashMap<String, Object>();
        result.put("_id", rs.getObject("id", UUID.class));
        result.put("key", rs.getString("section_key"));
        result.put("sectionNo", rs.getInt("section_no"));
        result.put("title", rs.getString("title"));
        result.put("contentHtml", rs.getString("content_html"));
        result.put("questionGroups", new ArrayList<Map<String, Object>>());
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Map<String, Object> values, String key) {
        return (List<Map<String, Object>>) values.get(key);
    }

    private void removeInternalId(Map<String, Object> section) {
        section.remove("_id");
        for (var group : list(section, "questionGroups")) {
            group.remove("_id");
            for (var question : list(group, "questions")) {
                question.remove("_id");
            }
        }
    }

    private List<StudentReadingAttemptResponse.SavedReadingResponse> savedResponseDtos(List<StoredResponse> responses) {
        return responses.stream().map(response -> new StudentReadingAttemptResponse.SavedReadingResponse(
                response.questionKey(), readMap(response.answer()), response.clientRevision(), response.answeredAt()
        )).toList();
    }

    private List<ReadingAutoGrader.QuestionDefinition> definitions(UUID versionId) {
        return jdbc.query("""
                select question_key, question_no, type_format, correct_answers::text correct_answers,
                  acceptable_answers::text acceptable_answers, explanation, max_score
                from public.test_version_questions where test_version_id = ? order by question_no
                """, (rs, ignored) -> new ReadingAutoGrader.QuestionDefinition(
                rs.getString("question_key"), rs.getInt("question_no"), rs.getString("type_format"),
                readStrings(rs.getString("correct_answers")), readStrings(rs.getString("acceptable_answers")),
                rs.getString("explanation"), rs.getBigDecimal("max_score")
        ), versionId);
    }

    private List<StoredResponse> storedResponses(UUID attemptId) {
        return jdbc.query("""
                select id, question_key, answer::text answer, is_correct, auto_score, client_revision, answered_at
                from public.test_attempt_responses where attempt_id = ? order by answered_at
                """, (rs, ignored) -> new StoredResponse(
                rs.getObject("id", UUID.class), rs.getString("question_key"), readNode(rs.getString("answer")),
                rs.getObject("is_correct", Boolean.class), rs.getBigDecimal("auto_score"),
                rs.getInt("client_revision"), rs.getObject("answered_at", OffsetDateTime.class)
        ), attemptId);
    }

    private AttemptContext loadAttempt(UUID attemptId, UUID studentId, boolean lock) {
        var values = jdbc.query(attemptSql() + " where attempt.id = ?" + (lock ? " for update of attempt" : ""),
                this::mapAttempt, attemptId);
        if (values.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy lượt làm bài");
        }
        var attempt = values.getFirst();
        if (!attempt.studentId().equals(studentId)) {
            throw new ResourceNotFoundException("Không tìm thấy lượt làm bài");
        }
        assertReadingAssignment(attempt);
        if (!memberships.hasActiveEnrollment(attempt.courseId(), studentId)) {
            throw new BusinessRuleException("Bạn không có ghi danh đang hoạt động trong khóa học này");
        }
        return attempt;
    }

    private AssignmentContext loadAssignment(UUID assignmentId, boolean lock) {
        var values = jdbc.query(assignmentSql() + " where assignment.id = ?" + (lock ? " for update of assignment" : ""),
                this::mapAssignment, assignmentId);
        if (values.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy bài kiểm tra được giao");
        }
        return values.getFirst();
    }

    private String assignmentSql() {
        return """
                select assignment.id assignment_id, assignment.test_version_id, assignment.course_id,
                  assignment.opens_at, assignment.closes_at, assignment.max_attempts, assignment.mode,
                  assignment.duration_seconds, assignment.show_result_after_submit, assignment.archived_at,
                  version.title, version.description, version.duration_minutes, version.primary_skill
                from public.test_assignments assignment
                join public.test_versions version on version.id = assignment.test_version_id
                """;
    }

    private String attemptSql() {
        return """
                select attempt.id attempt_id, attempt.test_assignment_id, attempt.test_version_id, attempt.student_id,
                  attempt.status, attempt.started_at, attempt.submitted_at, attempt.expires_at,
                  attempt.auto_score, attempt.final_score, assignment.course_id,
                  assignment.opens_at, assignment.closes_at, assignment.max_attempts, assignment.mode,
                  assignment.duration_seconds, assignment.show_result_after_submit, assignment.archived_at,
                  version.title, version.description, version.duration_minutes, version.primary_skill
                from public.test_attempts attempt
                join public.test_assignments assignment on assignment.id = attempt.test_assignment_id
                join public.test_versions version on version.id = attempt.test_version_id
                """;
    }

    private AssignmentContext mapAssignment(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        var assignment = new AssignmentContext(
                rs.getObject("assignment_id", UUID.class), rs.getObject("test_version_id", UUID.class),
                rs.getObject("course_id", UUID.class), rs.getObject("opens_at", OffsetDateTime.class),
                rs.getObject("closes_at", OffsetDateTime.class), rs.getShort("max_attempts"),
                rs.getString("mode"), rs.getObject("duration_seconds", Integer.class),
                rs.getBoolean("show_result_after_submit"), rs.getObject("archived_at", OffsetDateTime.class),
                rs.getString("title"), rs.getString("description"), rs.getInt("duration_minutes"),
                rs.getString("primary_skill")
        );
        assertReadingAssignment(assignment);
        return assignment;
    }

    private AttemptContext mapAttempt(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        var context = new AttemptContext(
                rs.getObject("attempt_id", UUID.class), rs.getObject("test_assignment_id", UUID.class),
                rs.getObject("test_version_id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getString("status"), rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("submitted_at", OffsetDateTime.class), rs.getObject("expires_at", OffsetDateTime.class),
                rs.getBigDecimal("auto_score"), rs.getBigDecimal("final_score"),
                rs.getObject("course_id", UUID.class), rs.getObject("opens_at", OffsetDateTime.class),
                rs.getObject("closes_at", OffsetDateTime.class), rs.getShort("max_attempts"), rs.getString("mode"),
                rs.getObject("duration_seconds", Integer.class), rs.getBoolean("show_result_after_submit"),
                rs.getObject("archived_at", OffsetDateTime.class), rs.getString("title"),
                rs.getString("description"), rs.getInt("duration_minutes"), rs.getString("primary_skill")
        );
        assertReadingAssignment(context);
        return context;
    }

    private void assertStudentMayAccess(AssignmentContextView assignment, UUID studentId) {
        assertReadingAssignment(assignment);
        if (assignment.archivedAt() != null) {
            throw new ResourceNotFoundException("Bài kiểm tra không còn khả dụng");
        }
        if (!memberships.hasActiveEnrollment(assignment.courseId(), studentId)) {
            throw new BusinessRuleException("Bạn không có ghi danh đang hoạt động trong khóa học này");
        }
    }

    private void assertReadingAssignment(AssignmentContextView assignment) {
        if (!"READING".equals(assignment.skill())) {
            throw new BusinessRuleException("Lượt làm này không phải đề Reading");
        }
    }

    private void assertInProgress(AttemptContext attempt) {
        if (!"IN_PROGRESS".equals(attempt.status())) {
            throw new ConflictException("Bài làm này không còn ở trạng thái đang làm");
        }
    }

    private void assertWithinAssignmentWindow(AssignmentContext assignment, OffsetDateTime now) {
        if (assignment.opensAt() != null && now.isBefore(assignment.opensAt())) {
            throw new BusinessRuleException("Bài kiểm tra chưa đến thời gian mở");
        }
        if (assignment.closesAt() != null && !now.isBefore(assignment.closesAt())) {
            throw new BusinessRuleException("Bài kiểm tra đã đóng");
        }
    }

    private OffsetDateTime expiresAt(AssignmentContext assignment, OffsetDateTime startedAt) {
        long duration = assignment.durationSeconds() == null
                ? Math.max(1, assignment.durationMinutes()) * 60L
                : assignment.durationSeconds();
        var expiresAt = startedAt.plusSeconds(duration);
        if (assignment.closesAt() != null && assignment.closesAt().isBefore(expiresAt)) {
            return assignment.closesAt();
        }
        return expiresAt;
    }

    private long remainingSeconds(OffsetDateTime expiresAt) {
        if (expiresAt == null) {
            return 0;
        }
        return Math.max(0, expiresAt.toEpochSecond() - now().toEpochSecond());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private JsonNode readNode(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleException("Không thể đọc đáp án đã lưu");
        }
    }

    private Map<String, Object> readMap(JsonNode value) {
        try {
            return objectMapper.convertValue(value, new TypeReference<>() {});
        } catch (IllegalArgumentException exception) {
            return Map.of();
        }
    }

    private Map<String, Object> readMap(String value) {
        return readMap(readNode(value));
    }

    private List<String> readStrings(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleException("Không thể đọc đáp án chuẩn hóa của đề");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleException("Không thể lưu dữ liệu JSON");
        }
    }

    private record AssignmentContext(
            UUID id,
            UUID testVersionId,
            UUID courseId,
            OffsetDateTime opensAt,
            OffsetDateTime closesAt,
            short maxAttempts,
            String mode,
            Integer durationSeconds,
            boolean showResultAfterSubmit,
            OffsetDateTime archivedAt,
            String title,
            String description,
            int durationMinutes,
            String skill
    ) implements AssignmentContextView {
    }

    private record AttemptContext(
            UUID id,
            UUID assignmentId,
            UUID testVersionId,
            UUID studentId,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime submittedAt,
            OffsetDateTime expiresAt,
            BigDecimal autoScore,
            BigDecimal finalScore,
            UUID courseId,
            OffsetDateTime opensAt,
            OffsetDateTime closesAt,
            short maxAttempts,
            String mode,
            Integer durationSeconds,
            boolean showResultAfterSubmit,
            OffsetDateTime archivedAt,
            String title,
            String description,
            int durationMinutes,
            String skill
    ) implements AssignmentContextView {
    }

    private interface AssignmentContextView {
        UUID courseId();
        OffsetDateTime archivedAt();
        String skill();
    }

    private record StoredResponse(
            UUID id,
            String questionKey,
            JsonNode answer,
            Boolean correct,
            BigDecimal autoScore,
            int clientRevision,
            OffsetDateTime answeredAt
    ) {
    }
}
