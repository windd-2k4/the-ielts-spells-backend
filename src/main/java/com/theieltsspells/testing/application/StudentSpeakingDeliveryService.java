package com.theieltsspells.testing.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.academic.application.AcademicMembershipService;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.testing.application.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentSpeakingDeliveryService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AcademicMembershipService memberships;
    private final SpeakingEvaluationService evaluationService;

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
                  and version.primary_skill = 'SPEAKING' and assignment.archived_at is null
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
    public StudentSpeakingAttemptResponse startOrResumeSelfPractice(UUID testVersionId, UUID studentId, boolean restart) {
        var versions = jdbc.query("""
                select version.id, version.duration_minutes, version.primary_skill
                from public.tests test join public.test_versions version on version.id=test.current_published_version_id
                where version.id=? and test.status='PUBLISHED' for update of test
                """, (rs, ignored) -> new VersionContext(rs.getObject("id", UUID.class),
                rs.getInt("duration_minutes"), rs.getString("primary_skill")), testVersionId);
        if (versions.isEmpty() || !"SPEAKING".equals(versions.getFirst().skill())) {
            throw new ResourceNotFoundException("Không tìm thấy đề Speaking đã xuất bản");
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
        var expiresAt = now.plusMinutes(Math.max(90, Math.max(1, versions.getFirst().durationMinutes()) * 3));
        UUID attemptId = jdbc.queryForObject("""
                insert into public.test_attempts(test_assignment_id, test_version_id, student_id, attempt_no,
                  status, started_at, expires_at, last_saved_at, attempt_origin)
                values (null, ?, ?, ?, 'IN_PROGRESS', ?, ?, ?, 'SELF_PRACTICE') returning id
                """, UUID.class, testVersionId, studentId,
                (short) Math.min(Short.MAX_VALUE, (used == null ? 0 : used) + 1), now, expiresAt, now);
        return payload(loadAttempt(attemptId, studentId, false));
    }

    @Transactional
    public StudentSpeakingAttemptResponse startOrResume(UUID assignmentId, UUID studentId) {
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

    public StudentSpeakingAttemptResponse getAttempt(UUID attemptId, UUID studentId) {
        return payload(loadAttempt(attemptId, studentId, false));
    }

    @Transactional
    public StudentSpeakingAttemptResponse resumeAttempt(UUID attemptId, UUID studentId) {
        var attempt = loadAttempt(attemptId, studentId, true);
        assertInProgress(attempt);
        var pausedRemaining = attempt.pausedRemainingSeconds();
        if ("SELF_PRACTICE".equals(attempt.origin()) && pausedRemaining != null) {
            if (pausedRemaining <= 0) {
                jdbc.update("""
                        update public.test_attempts
                        set expires_at = now() + interval '30 minutes',
                          paused_remaining_seconds = null, updated_at = now()
                        where id = ? and status = 'IN_PROGRESS'
                        """, attempt.id());
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
                || attempt.pausedRemainingSeconds() != null) return;
        jdbc.update("""
                update public.test_attempts
                set paused_remaining_seconds = ?,
                  updated_at = now()
                where id = ? and status = 'IN_PROGRESS'
                """, remainingSeconds(attempt.expiresAt()), attempt.id());
    }

    @Transactional
    public StudentSpeakingAttemptResponse saveResponses(UUID attemptId, SaveSpeakingResponsesRequest request, UUID studentId) {
        var attempt = loadAttempt(attemptId, studentId, true);
        if ("EXPIRED".equals(attempt.status()) && "SELF_PRACTICE".equals(attempt.origin())) {
            var extendedExpiresAt = now().plusMinutes(60);
            jdbc.update("update public.test_attempts set expires_at = ?, status = 'IN_PROGRESS', updated_at = now() where id = ?",
                    extendedExpiresAt, attempt.id());
            attempt = loadAttempt(attemptId, studentId, true);
        }
        assertInProgress(attempt);
        if (isExpired(attempt)) {
            if ("SELF_PRACTICE".equals(attempt.origin())) {
                var extendedExpiresAt = now().plusMinutes(60);
                jdbc.update("update public.test_attempts set expires_at = ?, status = 'IN_PROGRESS', updated_at = now() where id = ?",
                        extendedExpiresAt, attempt.id());
                attempt = loadAttempt(attemptId, studentId, true);
            } else {
                finalizeAttempt(attempt, "EXPIRED");
                throw new BusinessRuleException("Đã hết thời gian làm bài");
            }
        }

        for (var item : request.responses()) {
            Map<String, Object> answerMap = new LinkedHashMap<>();
            if (item.audioUrl() != null) answerMap.put("audioUrl", item.audioUrl());
            if (item.transcript() != null) answerMap.put("transcript", item.transcript());
            if (item.hintChoices() != null) answerMap.put("hintChoices", item.hintChoices());

            String answerJson = json(answerMap);
            jdbc.update("""
                    insert into public.test_attempt_responses(attempt_id, question_key, question_type, answer,
                      normalized_answer, max_score, client_revision, answered_at, updated_at)
                    values (?, ?, 'SPEAKING_QUESTION', cast(? as jsonb), '[]'::jsonb, 9, ?, now(), now())
                    on conflict (attempt_id, question_key) do update set
                      answer = excluded.answer,
                      client_revision = excluded.client_revision,
                      answered_at = now(),
                      updated_at = now()
                    where public.test_attempt_responses.client_revision <= excluded.client_revision
                    """, attempt.id(), item.questionKey(), answerJson, item.clientRevision());
        }

        jdbc.update("update public.test_attempts set last_saved_at=now(), updated_at=now() where id=?", attempt.id());
        return payload(loadAttempt(attempt.id(), studentId, false));
    }

    @Transactional
    public SpeakingEvaluationResponse evaluateQuestion(UUID attemptId, SpeakingEvaluationRequest request, UUID studentId) {
        var attempt = loadAttempt(attemptId, studentId, false);
        // Automatically save the latest transcript/audio response before evaluation
        Map<String, Object> answerMap = new LinkedHashMap<>();
        if (request.audioUrl() != null) answerMap.put("audioUrl", request.audioUrl());
        if (request.transcript() != null) answerMap.put("transcript", request.transcript());
        String answerJson = json(answerMap);

        jdbc.update("""
                insert into public.test_attempt_responses(attempt_id, question_key, question_type, answer,
                  normalized_answer, max_score, client_revision, answered_at, updated_at)
                values (?, ?, 'SPEAKING_QUESTION', cast(? as jsonb), '[]'::jsonb, 9, 1, now(), now())
                on conflict (attempt_id, question_key) do update set
                  answer = excluded.answer,
                  answered_at = now(),
                  updated_at = now()
                """, attempt.id(), request.questionKey(), answerJson);

        return evaluationService.evaluateAndSave(attemptId, studentId, request);
    }

    @Transactional
    public SpeakingAttemptResultResponse submit(UUID attemptId, UUID studentId) {
        var attempt = loadAttempt(attemptId, studentId, true);
        if ("SUBMITTED".equals(attempt.status()) || "GRADED".equals(attempt.status())) {
            return getResult(attemptId, studentId);
        }
        if ("EXPIRED".equals(attempt.status()) && "SELF_PRACTICE".equals(attempt.origin())) {
            jdbc.update("update public.test_attempts set status = 'IN_PROGRESS', updated_at = now() where id = ?", attempt.id());
            attempt = loadAttempt(attemptId, studentId, true);
        }
        assertInProgress(attempt);
        finalizeAttempt(attempt, "SUBMITTED");
        return getResult(attemptId, studentId);
    }

    public SpeakingAttemptResultResponse getResult(UUID attemptId, UUID studentId) {
        var attempt = loadAttempt(attemptId, studentId, false);
        var parts = parseParts(attempt.builderContent());
        var responses = responses(attempt.id());
        var evaluations = evaluations(attempt.id());

        return new SpeakingAttemptResultResponse(
                attempt.id(),
                attempt.status(),
                attempt.submittedAt(),
                attempt.showResult(),
                parts,
                responses,
                evaluations
        );
    }

    private StudentSpeakingAttemptResponse payload(AttemptContext attempt) {
        long remaining = remainingSeconds(attempt.expiresAt());
        var parts = parseParts(attempt.builderContent());
        var responses = responses(attempt.id());

        return new StudentSpeakingAttemptResponse(
                attempt.id(),
                attempt.testVersionId(),
                attempt.title(),
                attempt.description(),
                Math.max(15, (int) Math.ceil(remaining / 60.0)),
                remaining,
                attempt.pausedRemainingSeconds(),
                attempt.pausedRemainingSeconds() != null,
                attempt.status(),
                attempt.startedAt(),
                attempt.expiresAt(),
                attempt.showResult(),
                parts,
                responses
        );
    }

    private List<SpeakingSavedResponse> responses(UUID attemptId) {
        return jdbc.query("""
                select question_key, answer::text, client_revision, answered_at
                from public.test_attempt_responses where attempt_id=? and question_type='SPEAKING_QUESTION'
                order by question_key
                """, (rs, ignored) -> {
            String answerJson = rs.getString("answer");
            Map<String, Object> map = readMap(answerJson);
            String audioUrl = map.get("audioUrl") != null ? map.get("audioUrl").toString() : null;
            String transcript = map.get("transcript") != null ? map.get("transcript").toString() : null;
            Map<String, String> hintChoices = new HashMap<>();
            if (map.get("hintChoices") instanceof Map<?, ?> hintMap) {
                hintMap.forEach((k, v) -> {
                    if (k != null && v != null) hintChoices.put(k.toString(), v.toString());
                });
            }
            return new SpeakingSavedResponse(
                    rs.getString("question_key"),
                    audioUrl,
                    transcript,
                    hintChoices,
                    rs.getInt("client_revision"),
                    rs.getObject("answered_at", OffsetDateTime.class)
            );
        }, attemptId);
    }

    private List<SpeakingEvaluationResponse> evaluations(UUID attemptId) {
        return jdbc.query("""
                select question_key, status, overall_band, fluency_band, lexical_band,
                       grammar_band, pronunciation_band, fluency_feedback, lexical_feedback,
                       grammar_feedback, pronunciation_feedback, strengths::text, improvements::text,
                       updated_at
                from public.speaking_evaluations
                where attempt_id = ?
                order by created_at asc
                """, (rs, ignored) -> {
            Map<String, String> criteria = new LinkedHashMap<>();
            criteria.put("fluency_coherence", rs.getString("fluency_feedback"));
            criteria.put("lexical_resource", rs.getString("lexical_feedback"));
            criteria.put("grammatical_range_accuracy", rs.getString("grammar_feedback"));
            criteria.put("pronunciation", rs.getString("pronunciation_feedback"));

            List<String> strengths = readList(rs.getString("strengths"));
            List<String> improvements = readList(rs.getString("improvements"));

            return new SpeakingEvaluationResponse(
                    rs.getString("question_key"),
                    rs.getString("status"),
                    rs.getBigDecimal("overall_band"),
                    rs.getBigDecimal("fluency_band"),
                    rs.getBigDecimal("lexical_band"),
                    rs.getBigDecimal("grammar_band"),
                    rs.getBigDecimal("pronunciation_band"),
                    criteria,
                    strengths,
                    improvements,
                    rs.getObject("updated_at", OffsetDateTime.class)
            );
        }, attemptId);
    }

    @SuppressWarnings("unchecked")
    private List<SpeakingPartResponse> parseParts(Map<String, Object> content) {
        Object rawParts = content.get("parts");
        if (!(rawParts instanceof List<?> partList) || partList.isEmpty()) {
            return List.of();
        }

        List<SpeakingPartResponse> result = new ArrayList<>();
        for (int i = 0; i < partList.size(); i++) {
            if (!(partList.get(i) instanceof Map<?, ?> partMapRaw)) continue;
            var partMap = (Map<String, Object>) partMapRaw;

            int partNo = number(partMap.get("partNo"), i + 1);
            String partId = text(partMap.get("id"), "speaking-part-" + partNo);
            String topicTitle = text(partMap.get("topicTitle"), "Part " + partNo);
            String cueCardPromptHtml = text(partMap.get("cueCardPromptHtml"), "");
            String audioUrl = nullableText(partMap.get("audioUrl"));
            boolean hintsEnabled = !Boolean.FALSE.equals(partMap.get("hintsEnabled"));

            List<String> cueCardBullets = new ArrayList<>();
            if (partMap.get("cueCardBullets") instanceof List<?> bullets) {
                bullets.forEach(b -> { if (b != null) cueCardBullets.add(b.toString()); });
            }

            int prepTime = number(partMap.get("preparationTimeSeconds"), partNo == 2 ? 60 : 0);
            int ansTime = number(partMap.get("answerTimeSeconds"), partNo == 2 ? 120 : 30);

            List<String> followUps = new ArrayList<>();
            if (partMap.get("followUpQuestions") instanceof List<?> fList) {
                fList.forEach(f -> { if (f != null) followUps.add(f.toString()); });
            }

            List<SpeakingHintStepResponse> partHintSteps = parseHintSteps(partMap.get("hintSteps"));

            // Parse questions for Part 1 or Part 3
            List<SpeakingQuestionResponse> questions = new ArrayList<>();
            if (partMap.get("questions") instanceof List<?> qList) {
                int qIndex = 1;
                for (Object qObj : qList) {
                    if (!(qObj instanceof Map<?, ?> qMapRaw)) continue;
                    var qMap = (Map<String, Object>) qMapRaw;

                    String qId = text(qMap.get("id"), "spk-q-" + partNo + "-" + qIndex);
                    String promptText = text(qMap.get("promptText"), "");
                    String qAudioUrl = nullableText(qMap.get("audioUrl"));
                    boolean qHintsEnabled = !Boolean.FALSE.equals(qMap.get("hintsEnabled"));
                    List<SpeakingHintStepResponse> qHintSteps = parseHintSteps(qMap.get("hintSteps"));
                    String sampleResponse = nullableText(qMap.get("sampleResponseText"));
                    Integer qAnsTime = qMap.get("answerTimeSeconds") != null ? number(qMap.get("answerTimeSeconds"), ansTime) : ansTime;

                    questions.add(new SpeakingQuestionResponse(
                            qId,
                            qId,
                            qIndex,
                            promptText,
                            qAudioUrl,
                            qHintsEnabled,
                            qHintSteps,
                            sampleResponse,
                            qAnsTime
                    ));
                    qIndex++;
                }
            }

            // For Part 2, if no questions array, create a virtual question for the cue card
            if (partNo == 2 && questions.isEmpty()) {
                questions.add(new SpeakingQuestionResponse(
                        partId + "-cue",
                        partId + "-cue",
                        1,
                        topicTitle + (cueCardPromptHtml.isBlank() ? "" : ": " + cueCardPromptHtml),
                        audioUrl,
                        hintsEnabled,
                        partHintSteps,
                        nullableText(partMap.get("sampleResponseText")),
                        ansTime
                ));
            }

            result.add(new SpeakingPartResponse(
                    partId,
                    partNo,
                    topicTitle,
                    cueCardPromptHtml,
                    cueCardBullets,
                    audioUrl,
                    hintsEnabled,
                    partHintSteps,
                    prepTime,
                    ansTime,
                    followUps,
                    questions,
                    nullableText(partMap.get("sampleResponseText"))
            ));
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private List<SpeakingHintStepResponse> parseHintSteps(Object stepsObj) {
        if (!(stepsObj instanceof List<?> list)) return List.of();
        List<SpeakingHintStepResponse> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Map<?, ?> stepMapRaw)) continue;
            var stepMap = (Map<String, Object>) stepMapRaw;

            String id = text(stepMap.get("id"), "step-" + (i + 1));
            String title = text(stepMap.get("title"), "Bước " + (i + 1));
            String instruction = text(stepMap.get("instruction"), "");

            List<SpeakingHintOptionResponse> options = new ArrayList<>();
            if (stepMap.get("options") instanceof List<?> optList) {
                int optIndex = 1;
                for (Object optObj : optList) {
                    if (!(optObj instanceof Map<?, ?> optMapRaw)) continue;
                    var optMap = (Map<String, Object>) optMapRaw;
                    String optId = text(optMap.get("id"), "opt-" + optIndex);
                    String label = text(optMap.get("label"), "");
                    String phrase = text(optMap.get("phrase"), "");
                    String fullSentence = text(optMap.get("fullSentence"), phrase);
                    options.add(new SpeakingHintOptionResponse(optId, label, phrase, fullSentence));
                    optIndex++;
                }
            }
            result.add(new SpeakingHintStepResponse(id, title, instruction, options));
        }
        return result;
    }

    private void finalizeAttempt(AttemptContext attempt, String status) {
        jdbc.update("""
                update public.test_attempts
                set status = cast(? as public.attempt_status),
                  submitted_at = coalesce(submitted_at, now()),
                  last_saved_at = now(),
                  updated_at = now()
                where id = ?
                """, status, attempt.id());
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
        if (values.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy bài Speaking được giao");
        if (!"SPEAKING".equals(values.getFirst().skill())) throw new BusinessRuleException("Bài được giao không phải đề Speaking");
        return values.getFirst();
    }

    private AttemptContext loadAttempt(UUID id, UUID studentId, boolean lock) {
        var values = jdbc.query("""
                select attempt.id, attempt.test_assignment_id, attempt.test_version_id, attempt.student_id,
                  attempt.attempt_origin, attempt.status::text status, attempt.started_at, attempt.submitted_at,
                  attempt.expires_at, attempt.paused_remaining_seconds, assignment.course_id,
                  coalesce(assignment.show_result_after_submit, true) show_result,
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
            throw new ResourceNotFoundException("Không tìm thấy lượt làm bài Speaking");
        }
        var attempt = values.getFirst();
        if (!"SPEAKING".equals(attempt.skill())) throw new BusinessRuleException("Lượt làm này không phải đề Speaking");
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
        if (!"IN_PROGRESS".equals(attempt.status())) throw new BusinessRuleException("Bài làm đã hoàn thành hoặc hết hạn");
    }

    private boolean isExpired(AttemptContext attempt) {
        return attempt.expiresAt() != null && now().isAfter(attempt.expiresAt());
    }

    private long remainingSeconds(OffsetDateTime expiresAt) {
        if (expiresAt == null) return 0;
        long seconds = expiresAt.toEpochSecond() - now().toEpochSecond();
        return Math.max(0, seconds);
    }

    private OffsetDateTime expiresAt(Integer customSeconds, int durationMinutes, OffsetDateTime closesAt, OffsetDateTime now) {
        int seconds = customSeconds != null ? customSeconds : Math.max(1, durationMinutes) * 60;
        var standard = now.plusSeconds(seconds);
        return closesAt != null && closesAt.isBefore(standard) ? closesAt : standard;
    }

    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private int number(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private String text(Object value, String fallback) {
        return value instanceof String s && !s.isBlank() ? s.trim() : fallback;
    }

    private String nullableText(Object value) {
        return value instanceof String s && !s.isBlank() ? s.trim() : null;
    }

    private record VersionContext(UUID id, int durationMinutes, String skill) {}
    private record AssignmentContext(UUID id, UUID testVersionId, UUID courseId, OffsetDateTime opensAt,
                                     OffsetDateTime closesAt, short maxAttempts, Integer durationSeconds,
                                     boolean showResultAfterSubmit, OffsetDateTime archivedAt,
                                     int durationMinutes, String skill) {}
    private record AttemptContext(UUID id, UUID testAssignmentId, UUID testVersionId, UUID studentId,
                                  String origin, String status, OffsetDateTime startedAt, OffsetDateTime submittedAt,
                                  OffsetDateTime expiresAt, Long pausedRemainingSeconds, UUID courseId,
                                  boolean showResult, String title, String description, String skill,
                                  Map<String, Object> builderContent) {}
}
