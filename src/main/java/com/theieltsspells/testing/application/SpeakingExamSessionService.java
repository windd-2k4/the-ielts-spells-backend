package com.theieltsspells.testing.application;

import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.CodedApiException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.testing.application.dto.AbortSpeakingExamSessionRequest;
import com.theieltsspells.testing.application.dto.SpeakingExamPartTwoReadyRequest;
import com.theieltsspells.testing.application.dto.SpeakingExamMode;
import com.theieltsspells.testing.application.dto.SpeakingExaminerSessionResponse;
import com.theieltsspells.testing.application.dto.SpeakingExaminerTurnResponse;
import com.theieltsspells.testing.application.dto.SpeakingExaminerTurnSubmissionResponse;
import com.theieltsspells.testing.application.dto.SubmitSpeakingExaminerTurnRequest;
import com.theieltsspells.testing.domain.SpeakingExamState;
import com.theieltsspells.testing.domain.SpeakingExamStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Persists one immutable, question-bank-only route per attempt. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpeakingExamSessionService {
    private final JdbcTemplate jdbc;
    private final SpeakingAudioService audioService;
    private final SpeakingExaminerQuestionPlanner planner;
    private final SpeakingExaminerSpeechService speechService;

    @Value("${app.speaking-examiner.enabled:false}")
    private boolean enabled;
    @Value("${app.speaking-examiner.prompt-version:speaking-examiner-v1}")
    private String promptVersion;
    @Value("${app.speaking-examiner.rubric-version:ielts-speaking-public-v1}")
    private String rubricVersion;
    @Value("${app.speaking-examiner.feature-extractor-version:unconfigured}")
    private String featureExtractorVersion;

    @Transactional
    public SpeakingExaminerSessionResponse createOrResume(UUID attemptId, UUID studentId) {
        requireEnabled();
        AttemptRow attempt = loadAttempt(attemptId, studentId, true);
        var existing = sessionsByAttempt(attemptId, studentId);
        if (!existing.isEmpty()) return response(existing.getFirst());
        if (!"IN_PROGRESS".equals(attempt.status())) {
            throw new BusinessRuleException("Bài làm đã hoàn thành hoặc hết hạn");
        }

        var plan = planner.plan(attempt.builderContent());
        SpeakingExamStateMachine.requireTransition(SpeakingExamState.CREATED, SpeakingExamState.MIC_CHECK);
        UUID sessionId = UUID.randomUUID();
        jdbc.update("""
                insert into public.speaking_exam_sessions(
                  id, attempt_id, student_id, state, state_version, prompt_version, rubric_version,
                  speech_model_version, feature_extractor_version)
                values (?, ?, ?, 'MIC_CHECK', 1, ?, ?, ?, ?)
                """, sessionId, attemptId, studentId, promptVersion, rubricVersion,
                speechService.modelVersion(), featureExtractorVersion);
        int sequenceNo = 1;
        for (var planned : plan.turns()) insertTurn(sessionId, sequenceNo++, planned);
        return response(loadSession(sessionId, studentId, false));
    }

    public SpeakingExaminerSessionResponse get(UUID sessionId, UUID studentId) {
        requireEnabled();
        return response(loadSession(sessionId, studentId, false));
    }

    @Transactional
    public SpeakingExaminerTurnSubmissionResponse submitTurn(UUID sessionId,
                                                               SubmitSpeakingExaminerTurnRequest request,
                                                               UUID studentId) {
        requireEnabled();
        SessionRow session = loadSession(sessionId, studentId, true);
        var accepted = acceptedTurn(sessionId, request.clientTurnId());
        if (!accepted.isEmpty()) return acceptedResponse(session, accepted.getFirst());
        if (session.stateVersion() != request.stateVersion() || !isAnswerState(session.state())) throw stateConflict();
        if (!"IN_PROGRESS".equals(session.attemptStatus())) throw stateConflict();

        TurnRow current = currentTurn(sessionId, session.state());
        if (current == null || !current.id().equals(request.examinerTurnId())) throw stateConflict();
        int durationMs = audioService.durationMsForOwnedStorageKey(
                session.attemptId(), studentId, request.storageKey(), current.id().toString());
        String transcript = speechService.transcribe(request.storageKey()).text().trim();
        SpeakingExamState nextState = nextState(session, current);
        SpeakingExamStateMachine.requireTransition(session.state(), nextState);
        long nextVersion = session.stateVersion() + 1;
        UUID studentTurnId = UUID.randomUUID();
        boolean completed = nextState == SpeakingExamState.EVALUATION_QUEUED;
        TurnRow nextTurn = completed ? null : nextTurn(sessionId, nextState);

        int savedTurn = jdbc.update("""
                update public.speaking_exam_turns set client_turn_id=?, student_turn_id=?, answer_storage_key=?,
                  transcript=?, duration_ms=?, submitted_at=now(), accepted_state=?, accepted_state_version=?,
                  accepted_next_turn_id=?
                where id=? and session_id=? and submitted_at is null
                """, request.clientTurnId(), studentTurnId, request.storageKey(), transcript, durationMs,
                nextState.name(), nextVersion, nextTurn == null ? null : nextTurn.id(), current.id(), sessionId);
        if (savedTurn != 1) throw stateConflict();

        int moved = jdbc.update("""
                update public.speaking_exam_sessions set state=?, state_version=?,
                  completed_at=case when ? then now() else completed_at end, updated_at=now()
                where id=? and state=? and state_version=?
                """, nextState.name(), nextVersion, completed, sessionId, session.state().name(), session.stateVersion());
        if (moved != 1) throw stateConflict();

        if (completed) finalizeAttempt(session.attemptId(), studentId);
        SpeakingExaminerTurnResponse nextTurnResponse = turn(nextTurn);
        return new SpeakingExaminerTurnSubmissionResponse(sessionId, studentTurnId, nextState, nextVersion,
                0, nextTurnResponse, completed);
    }

    @Transactional
    public SpeakingExaminerSessionResponse markPartTwoReady(UUID sessionId,
                                                             SpeakingExamPartTwoReadyRequest request,
                                                             UUID studentId) {
        requireEnabled();
        SessionRow session = loadSession(sessionId, studentId, true);
        move(session, request.stateVersion(), SpeakingExamState.PART_2_LONG_TURN);
        return response(loadSession(sessionId, studentId, false));
    }

    @Transactional
    public SpeakingExaminerSessionResponse abort(UUID sessionId,
                                                  AbortSpeakingExamSessionRequest request,
                                                  UUID studentId) {
        requireEnabled();
        SessionRow session = loadSession(sessionId, studentId, true);
        if (session.state() == SpeakingExamState.ABORTED) return response(session);
        move(session, request.stateVersion(), SpeakingExamState.ABORTED);
        return response(loadSession(sessionId, studentId, false));
    }

    public SpeakingExaminerSpeechService.AudioContent promptAudio(UUID sessionId, UUID turnId, UUID studentId) {
        requireEnabled();
        return speechService.openPrompt(sessionId, turnId, studentId);
    }

    private void move(SessionRow session, long expectedVersion, SpeakingExamState target) {
        if (session.stateVersion() != expectedVersion) throw stateConflict();
        SpeakingExamStateMachine.requireTransition(session.state(), target);
        int moved = jdbc.update("""
                update public.speaking_exam_sessions set state=?, state_version=state_version+1, updated_at=now()
                where id=? and state=? and state_version=?
                """, target.name(), session.id(), session.state().name(), expectedVersion);
        if (moved != 1) throw stateConflict();
    }

    private SpeakingExamState nextState(SessionRow session, TurnRow current) {
        return switch (session.state()) {
            case MIC_CHECK -> SpeakingExamState.IDENTITY_CHECK;
            case IDENTITY_CHECK -> SpeakingExamState.PART_1;
            case PART_1 -> hasPendingTurn(session.id(), SpeakingExamState.PART_1, current.id())
                    ? SpeakingExamState.PART_1 : SpeakingExamState.PART_2_PREPARATION;
            case PART_2_LONG_TURN -> SpeakingExamState.PART_2_FOLLOW_UP;
            case PART_2_FOLLOW_UP -> SpeakingExamState.PART_3;
            case PART_3 -> hasPendingTurn(session.id(), SpeakingExamState.PART_3, current.id())
                    ? SpeakingExamState.PART_3 : SpeakingExamState.CLOSING;
            case CLOSING -> SpeakingExamState.EVALUATION_QUEUED;
            default -> throw stateConflict();
        };
    }

    private boolean isAnswerState(SpeakingExamState state) {
        return switch (state) {
            case MIC_CHECK, IDENTITY_CHECK, PART_1, PART_2_LONG_TURN, PART_2_FOLLOW_UP, PART_3, CLOSING -> true;
            default -> false;
        };
    }

    private boolean hasPendingTurn(UUID sessionId, SpeakingExamState state, UUID excludingTurnId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from public.speaking_exam_turns
                where session_id=? and exam_state=? and submitted_at is null and id<>?
                """, Integer.class, sessionId, state.name(), excludingTurnId);
        return count != null && count > 0;
    }

    private SpeakingExaminerSessionResponse response(SessionRow session) {
        return new SpeakingExaminerSessionResponse(session.id(), session.attemptId(), SpeakingExamMode.AI_EXAMINER,
                session.state(), session.stateVersion(), 0,
                new SpeakingExaminerSessionResponse.Policy(0, false, true,
                        SpeakingExaminerSessionResponse.ResultType.ESTIMATED_BAND),
                new SpeakingExaminerSessionResponse.Versions(session.promptVersion(), session.rubricVersion(),
                        session.speechModelVersion(), session.featureExtractorVersion()),
                currentTurnResponse(session.id(), session.state()), session.startedAt(), session.completedAt());
    }

    private SpeakingExaminerTurnResponse currentTurnResponse(UUID sessionId, SpeakingExamState state) {
        if (state == SpeakingExamState.PART_2_PREPARATION) {
            return turn(currentTurn(sessionId, SpeakingExamState.PART_2_LONG_TURN));
        }
        return isAnswerState(state) ? turn(currentTurn(sessionId, state)) : null;
    }

    private UUID insertTurn(UUID sessionId, int sequenceNo, SpeakingExaminerQuestionPlanner.PlannedTurn planned) {
        UUID id = UUID.randomUUID();
        var audio = speechService.promptAudio(planned.promptText());
        jdbc.update("""
                insert into public.speaking_exam_turns(
                  id, session_id, sequence_no, exam_state, part_no, kind, topic_key, prompt_text,
                  prompt_audio_cache_key, preparation_time_seconds, answer_time_seconds)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, sessionId, sequenceNo, planned.state().name(), planned.partNo(), planned.kind().name(),
                planned.topicKey(), planned.promptText(), audio.cacheKey(), planned.preparationTimeSeconds(),
                planned.answerTimeSeconds());
        return id;
    }

    private AttemptRow loadAttempt(UUID attemptId, UUID studentId, boolean lock) {
        var values = jdbc.query("""
                select attempt.status::text status, version.builder_content::text builder_content
                from public.test_attempts attempt
                join public.test_versions version on version.id=attempt.test_version_id
                where attempt.id=? and attempt.student_id=? and version.primary_skill='SPEAKING'
                """ + (lock ? " for update of attempt" : ""), (rs, ignored) -> new AttemptRow(
                rs.getString("status"), rs.getString("builder_content")), attemptId, studentId);
        if (values.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy lượt làm bài Speaking");
        return values.getFirst();
    }

    private List<SessionRow> sessionsByAttempt(UUID attemptId, UUID studentId) {
        return jdbc.query(sessionSelect() + " where exam_session.attempt_id=? and exam_session.student_id=?",
                this::mapSession, attemptId, studentId);
    }

    private SessionRow loadSession(UUID sessionId, UUID studentId, boolean lock) {
        var values = jdbc.query(sessionSelect() + " where exam_session.id=? and exam_session.student_id=?"
                        + (lock ? " for update of exam_session, attempt" : ""),
                this::mapSession, sessionId, studentId);
        if (values.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy phiên AI Speaking Examiner");
        return values.getFirst();
    }

    private String sessionSelect() {
        return """
                select exam_session.id, exam_session.attempt_id, exam_session.state, exam_session.state_version,
                  exam_session.prompt_version, exam_session.rubric_version, exam_session.speech_model_version,
                  exam_session.feature_extractor_version, exam_session.started_at, exam_session.completed_at,
                  attempt.status::text attempt_status
                from public.speaking_exam_sessions exam_session
                join public.test_attempts attempt on attempt.id=exam_session.attempt_id
                """;
    }

    private SessionRow mapSession(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        return new SessionRow(rs.getObject("id", UUID.class), rs.getObject("attempt_id", UUID.class),
                SpeakingExamState.valueOf(rs.getString("state")), rs.getLong("state_version"),
                rs.getString("prompt_version"), rs.getString("rubric_version"),
                rs.getString("speech_model_version"), rs.getString("feature_extractor_version"),
                rs.getObject("started_at", OffsetDateTime.class), rs.getObject("completed_at", OffsetDateTime.class),
                rs.getString("attempt_status"));
    }

    private TurnRow currentTurn(UUID sessionId, SpeakingExamState state) {
        var values = jdbc.query("""
                select id, session_id, sequence_no, exam_state, part_no, kind, topic_key, prompt_text,
                  prompt_audio_cache_key, preparation_time_seconds, answer_time_seconds
                from public.speaking_exam_turns
                where session_id=? and exam_state=? and submitted_at is null order by sequence_no limit 1
                """, this::mapTurn, sessionId, state.name());
        return values.isEmpty() ? null : values.getFirst();
    }

    private TurnRow nextTurn(UUID sessionId, SpeakingExamState state) {
        if (state == SpeakingExamState.PART_2_PREPARATION) {
            return currentTurn(sessionId, SpeakingExamState.PART_2_LONG_TURN);
        }
        return isAnswerState(state) ? currentTurn(sessionId, state) : null;
    }

    private SpeakingExaminerTurnResponse turn(TurnRow value) {
        if (value == null) return null;
        return new SpeakingExaminerTurnResponse(value.id(), value.sequenceNo(), value.partNo(), value.kind(),
                value.topicKey(), value.promptText(), SpeakingExaminerSpeechService.audioPath(value.sessionId(), value.id()),
                value.preparationTimeSeconds(), value.answerTimeSeconds());
    }

    private TurnRow mapTurn(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        return new TurnRow(rs.getObject("id", UUID.class), rs.getObject("session_id", UUID.class),
                rs.getInt("sequence_no"), SpeakingExamState.valueOf(rs.getString("exam_state")),
                rs.getObject("part_no", Integer.class), SpeakingExaminerTurnResponse.Kind.valueOf(rs.getString("kind")),
                rs.getString("topic_key"), rs.getString("prompt_text"), rs.getString("prompt_audio_cache_key"),
                rs.getObject("preparation_time_seconds", Integer.class),
                rs.getObject("answer_time_seconds", Integer.class));
    }

    private List<AcceptedTurn> acceptedTurn(UUID sessionId, UUID clientTurnId) {
        return jdbc.query("""
                select student_turn_id, accepted_state, accepted_state_version, accepted_next_turn_id
                from public.speaking_exam_turns where session_id=? and client_turn_id=?
                """, (rs, ignored) -> new AcceptedTurn(rs.getObject("student_turn_id", UUID.class),
                SpeakingExamState.valueOf(rs.getString("accepted_state")), rs.getLong("accepted_state_version"),
                rs.getObject("accepted_next_turn_id", UUID.class)),
                sessionId, clientTurnId);
    }

    private SpeakingExaminerTurnSubmissionResponse acceptedResponse(SessionRow session, AcceptedTurn accepted) {
        boolean completed = accepted.state() == SpeakingExamState.EVALUATION_QUEUED;
        return new SpeakingExaminerTurnSubmissionResponse(session.id(), accepted.studentTurnId(), accepted.state(),
                accepted.stateVersion(), 0, completed ? null : turn(loadTurn(accepted.nextTurnId())), completed);
    }

    private TurnRow loadTurn(UUID turnId) {
        if (turnId == null) return null;
        var values = jdbc.query("""
                select id, session_id, sequence_no, exam_state, part_no, kind, topic_key, prompt_text,
                  prompt_audio_cache_key, preparation_time_seconds, answer_time_seconds
                from public.speaking_exam_turns where id=?
                """, this::mapTurn, turnId);
        return values.isEmpty() ? null : values.getFirst();
    }

    private void finalizeAttempt(UUID attemptId, UUID studentId) {
        int finalized = jdbc.update("""
                update public.test_attempts set status=cast('SUBMITTED' as public.attempt_status),
                  submitted_at=coalesce(submitted_at, now()), last_saved_at=now(), updated_at=now()
                where id=? and student_id=? and status='IN_PROGRESS'
                """, attemptId, studentId);
        if (finalized != 1) throw stateConflict();
    }

    private void requireEnabled() {
        if (!enabled) throw new CodedApiException(HttpStatus.NOT_FOUND, "AI_SPEAKING_EXAMINER_DISABLED",
                "AI Speaking Examiner chưa được bật");
    }

    private CodedApiException stateConflict() {
        return new CodedApiException(HttpStatus.CONFLICT, "SPEAKING_EXAMINER_STATE_CONFLICT",
                "Phiên Speaking đã thay đổi; vui lòng tải lại phiên hiện tại");
    }

    private record AttemptRow(String status, String builderContent) {}

    private record SessionRow(UUID id, UUID attemptId, SpeakingExamState state, long stateVersion,
                              String promptVersion, String rubricVersion, String speechModelVersion,
                              String featureExtractorVersion, OffsetDateTime startedAt, OffsetDateTime completedAt,
                              String attemptStatus) {}

    private record TurnRow(UUID id, UUID sessionId, int sequenceNo, SpeakingExamState state, Integer partNo,
                           SpeakingExaminerTurnResponse.Kind kind, String topicKey, String promptText,
                           String promptAudioCacheKey, Integer preparationTimeSeconds, Integer answerTimeSeconds) {}

    private record AcceptedTurn(UUID studentTurnId, SpeakingExamState state, long stateVersion,
                                UUID nextTurnId) {}
}
