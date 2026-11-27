package com.theieltsspells.testing.application;

import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.CodedApiException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.testing.application.dto.AbortSpeakingExamSessionRequest;
import com.theieltsspells.testing.application.dto.CompleteSpeakingExamSessionRequest;
import com.theieltsspells.testing.application.dto.SpeakingExamPartTwoReadyRequest;
import com.theieltsspells.testing.application.dto.SpeakingExamMode;
import com.theieltsspells.testing.application.dto.SpeakingExaminerSessionResponse;
import com.theieltsspells.testing.application.dto.SpeakingExaminerTurnResponse;
import com.theieltsspells.testing.application.dto.SpeakingExaminerTurnSubmissionResponse;
import com.theieltsspells.testing.application.dto.SubmitSpeakingExaminerTurnRequest;
import com.theieltsspells.testing.domain.SpeakingExamState;
import com.theieltsspells.testing.domain.SpeakingExamStateMachine;
import com.theieltsspells.testing.domain.SpeakingSessionVariant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Persists session state, adaptive Part 3 follow-ups, and Part 2 server deadlines per attempt. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SpeakingExamSessionService {
    private final JdbcTemplate jdbc;
    private final SpeakingAudioService audioService;
    private final SpeakingExaminerQuestionPlanner planner;
    private final SpeakingExaminerSpeechService speechService;
    private final SpeakingEvaluationService evaluationService;

    @Value("${app.speaking-examiner.enabled:false}")
    private boolean enabled;
    @Value("${app.speaking-examiner.prompt-version:speaking-examiner-v1}")
    private String promptVersion;
    @Value("${app.speaking-examiner.rubric-version:ielts-speaking-public-v1}")
    private String rubricVersion;
    @Value("${app.speaking-examiner.feature-extractor-version:unconfigured}")
    private String featureExtractorVersion;
    @Value("${app.speaking-examiner.part-2-enabled:false}")
    private boolean partTwoEnabled;
    @Value("${app.speaking-examiner.part-3-enabled:false}")
    private boolean partThreeEnabled;
    @Value("${app.speaking-examiner.adaptive-follow-up-enabled:true}")
    private boolean adaptiveFollowUpEnabled;
    @Value("${app.speaking-examiner.max-adaptive-follow-ups:2}")
    private int maxAdaptiveFollowUps;

    @Transactional
    public SpeakingExaminerSessionResponse createOrResume(UUID attemptId, UUID studentId) {
        return createOrResume(attemptId, studentId, SpeakingSessionVariant.FULL_MOCK);
    }

    @Transactional
    public SpeakingExaminerSessionResponse createOrResume(UUID attemptId, UUID studentId,
                                                           SpeakingSessionVariant requestedVariant) {
        requireEnabled();
        SpeakingSessionVariant variant = requestedVariant == null
                ? SpeakingSessionVariant.FULL_MOCK : requestedVariant;
        if (variant == SpeakingSessionVariant.PART_2 && !partTwoEnabled) {
            throw new CodedApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SPEAKING_PART_2_DISABLED",
                    "Luyện riêng Part 2 chưa được bật trên môi trường này");
        }
        if (variant == SpeakingSessionVariant.PART_3 && !partThreeEnabled) {
            throw new CodedApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SPEAKING_PART_3_DISABLED",
                    "Luyện riêng Part 3 chưa được bật trên môi trường này");
        }
        AttemptRow attempt = loadAttempt(attemptId, studentId, true);
        var existing = sessionsByAttempt(attemptId, studentId);
        if (!existing.isEmpty()) {
            SessionRow session = existing.getFirst();
            if (session.variant() != variant) throw variantConflict(session.variant());
            return response(session);
        }
        if (!"IN_PROGRESS".equals(attempt.status())) {
            throw new BusinessRuleException("Bài làm đã hoàn thành hoặc hết hạn");
        }

        var plan = planner.plan(attempt.builderContent(), variant);
        SpeakingExamStateMachine.requireTransition(variant, SpeakingExamState.CREATED, SpeakingExamState.MIC_CHECK);
        UUID sessionId = UUID.randomUUID();
        jdbc.update("""
                insert into public.speaking_exam_sessions(
                  id, attempt_id, student_id, state, state_version, prompt_version, rubric_version,
                  speech_model_version, feature_extractor_version, variant)
                values (?, ?, ?, 'MIC_CHECK', 1, ?, ?, ?, ?, ?)
                """, sessionId, attemptId, studentId, promptVersion, rubricVersion,
                speechService.modelVersion(), featureExtractorVersion, variant.name());
        int sequenceNo = 100;
        for (var planned : plan.turns()) {
            insertTurn(sessionId, sequenceNo, planned);
            sequenceNo += 100;
        }
        return response(loadSession(sessionId, studentId, false));
    }

    public SpeakingExaminerSessionResponse get(UUID sessionId, UUID studentId) {
        requireEnabled();
        return response(loadSession(sessionId, studentId, false));
    }

    @Transactional(readOnly = true)
    public TurnPreparation prepareTurn(UUID sessionId,
                                       SubmitSpeakingExaminerTurnRequest request,
                                       UUID studentId) {
        requireEnabled();
        SessionRow session = loadSession(sessionId, studentId, false);
        var accepted = acceptedTurn(sessionId, request.clientTurnId());
        if (!accepted.isEmpty()) {
            return TurnPreparation.accepted(acceptedResponse(session, accepted.getFirst()));
        }
        if (session.stateVersion() != request.stateVersion() || !isAnswerState(session.state())) throw stateConflict();
        if (!"IN_PROGRESS".equals(session.attemptStatus())) throw stateConflict();

        TurnRow current = currentTurn(sessionId, session.state());
        if (current == null || !current.id().equals(request.examinerTurnId())) throw stateConflict();
        int durationMs = audioService.durationMsForOwnedStorageKey(
                session.attemptId(), studentId, request.storageKey(), current.id().toString());
        SpeakingAdaptiveFollowUpService.Context adaptiveContext = buildAdaptiveContext(session, current);
        return new TurnPreparation(null, durationMs, adaptiveContext);
    }

    @Transactional
    public SpeakingExaminerTurnSubmissionResponse commitTurn(UUID sessionId,
                                                               SubmitSpeakingExaminerTurnRequest request,
                                                               UUID studentId,
                                                               int durationMs,
                                                               String transcript,
                                                               long storageReadMs,
                                                               long sttProcessingMs,
                                                               long followUpGenerationMs,
                                                               long ttsProcessingMs,
                                                               long turnStartedNanos,
                                                               String adaptivePrompt,
                                                               String adaptiveAudioCacheKey) {
        requireEnabled();
        SessionRow session = loadSession(sessionId, studentId, true);
        var accepted = acceptedTurn(sessionId, request.clientTurnId());
        if (!accepted.isEmpty()) return acceptedResponse(session, accepted.getFirst());
        if (session.stateVersion() != request.stateVersion() || !isAnswerState(session.state())) throw stateConflict();
        if (!"IN_PROGRESS".equals(session.attemptStatus())) throw stateConflict();

        TurnRow current = currentTurn(sessionId, session.state());
        if (current == null || !current.id().equals(request.examinerTurnId())) throw stateConflict();

        boolean adaptiveInserted = false;
        int nextSequenceNo = current.sequenceNo() + 1;
        if (adaptivePrompt != null && !adaptivePrompt.isBlank()
                && adaptiveAudioCacheKey != null && !adaptiveAudioCacheKey.isBlank()
                && adaptiveFollowUpEnabled
                && session.state() == SpeakingExamState.PART_3
                && session.adaptiveFollowUpsUsed() < maxAdaptiveFollowUps
                && sequenceSlotFree(sessionId, nextSequenceNo)) {
            insertAdaptiveTurn(sessionId, nextSequenceNo, current, adaptivePrompt, adaptiveAudioCacheKey);
            adaptiveInserted = true;
        }

        SpeakingExamState nextState = nextState(session, current);
        SpeakingExamStateMachine.requireTransition(session.variant(), session.state(), nextState);
        long nextVersion = session.stateVersion() + 1;
        UUID studentTurnId = UUID.randomUUID();
        boolean completed = nextState == SpeakingExamState.EVALUATION_QUEUED;
        TurnRow nextTurn = completed ? null : nextTurn(sessionId, nextState, current.id());

        int savedTurn = jdbc.update("""
                update public.speaking_exam_turns set client_turn_id=?, student_turn_id=?, answer_storage_key=?,
                  transcript=?, duration_ms=?, submitted_at=now(), accepted_state=?, accepted_state_version=?,
                  accepted_next_turn_id=?
                where id=? and session_id=? and submitted_at is null
                """, request.clientTurnId(), studentTurnId, request.storageKey(), transcript, durationMs,
                nextState.name(), nextVersion, nextTurn == null ? null : nextTurn.id(), current.id(), sessionId);
        if (savedTurn != 1) throw stateConflict();

        boolean enteringPart2Prep = nextState == SpeakingExamState.PART_2_PREPARATION;
        boolean leavingPart2Prep = session.state() == SpeakingExamState.PART_2_PREPARATION && !enteringPart2Prep;
        int prepSeconds = enteringPart2Prep && nextTurn != null && nextTurn.preparationTimeSeconds() != null
                && nextTurn.preparationTimeSeconds() > 0 ? nextTurn.preparationTimeSeconds() : 60;
        int adaptiveIncrement = adaptiveInserted ? 1 : 0;

        int moved = jdbc.update("""
                update public.speaking_exam_sessions set state=?, state_version=?,
                  adaptive_follow_ups_used = adaptive_follow_ups_used + ?,
                  part_2_prep_deadline_at = case
                    when ? then now() + (? * interval '1 second')
                    when ? then null
                    else part_2_prep_deadline_at
                  end,
                  completed_at=case when ? then now() else completed_at end,
                  updated_at=now()
                where id=? and state=? and state_version=?
                """, nextState.name(), nextVersion, adaptiveIncrement,
                enteringPart2Prep, prepSeconds, leavingPart2Prep,
                completed, sessionId, session.state().name(), session.stateVersion());
        if (moved != 1) throw stateConflict();

        if (completed) {
            finalizeAttempt(session.attemptId(), studentId);
            evaluationService.registerAttempt(session.attemptId(), studentId);
            if (evaluationService.isAiEligible(studentId)) {
                evaluationService.requestAi(session.attemptId(), studentId);
            }
        }
        SessionRow refreshed = loadSession(sessionId, studentId, false);
        SpeakingExaminerTurnResponse nextTurnResponse = turn(nextTurn);
        long totalTurnMs = (System.nanoTime() - turnStartedNanos) / 1_000_000;
        String endReason = request.endReason() == null ? "UNSPECIFIED" : request.endReason().name();
        Integer clientUploadMs = request.clientUploadMs();
        double realTimeFactor = durationMs <= 0 ? 0d : (double) sttProcessingMs / durationMs;
        log.info("event=speaking_turn_processed sessionId={} turnId={} variant={} state={} endReason={} "
                        + "clientUploadMs={} audioDurationMs={} storageReadMs={} sttQueueMs=0 sttProcessingMs={} "
                        + "followUpGenerationMs={} ttsProcessingMs={} adaptiveFollowUpInserted={} "
                        + "realTimeFactor={} totalTurnMs={}",
                sessionId, current.id(), session.variant(), session.state(), endReason, clientUploadMs,
                durationMs, storageReadMs, sttProcessingMs, followUpGenerationMs, ttsProcessingMs,
                adaptiveInserted, String.format(java.util.Locale.ROOT, "%.3f", realTimeFactor), totalTurnMs);
        var latency = new SpeakingExaminerTurnSubmissionResponse.LatencyMetrics(
                storageReadMs, sttProcessingMs, followUpGenerationMs, ttsProcessingMs, totalTurnMs);
        return new SpeakingExaminerTurnSubmissionResponse(sessionId, studentTurnId, nextState, nextVersion,
                refreshed.adaptiveFollowUpsUsed(), nextTurnResponse, completed, latency,
                refreshed.part2PrepDeadlineAt(), refreshed.part2PrepRemainingSeconds());
    }

    @Transactional
    public SpeakingExaminerTurnSubmissionResponse submitTurn(UUID sessionId,
                                                               SubmitSpeakingExaminerTurnRequest request,
                                                               UUID studentId) {
        TurnPreparation prepared = prepareTurn(sessionId, request, studentId);
        if (prepared.alreadyAccepted() != null) return prepared.alreadyAccepted();
        long turnStarted = System.nanoTime();
        var timedTranscription = speechService.transcribeTimed(request.storageKey());
        String transcript = timedTranscription.transcription().text().trim();
        return commitTurn(sessionId, request, studentId, prepared.durationMs(), transcript,
                timedTranscription.storageReadMs(), timedTranscription.sttProcessingMs(),
                0L, 0L, turnStarted, null, null);
    }

    @Transactional
    public SpeakingExaminerSessionResponse markPartTwoReady(UUID sessionId,
                                                             SpeakingExamPartTwoReadyRequest request,
                                                             UUID studentId) {
        requireEnabled();
        SessionRow session = loadSession(sessionId, studentId, true);
        if (session.state() == SpeakingExamState.PART_2_LONG_TURN
                && session.stateVersion() == request.stateVersion() + 1) {
            return response(session);
        }
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

    @Transactional
    public SpeakingExaminerSessionResponse completeClosing(UUID sessionId,
                                                            CompleteSpeakingExamSessionRequest request,
                                                            UUID studentId) {
        requireEnabled();
        SessionRow session = loadSession(sessionId, studentId, true);
        if (session.state() == SpeakingExamState.EVALUATION_QUEUED) return response(session);
        if (session.state() != SpeakingExamState.CLOSING || session.stateVersion() != request.stateVersion()) {
            throw stateConflict();
        }
        SpeakingExamStateMachine.requireTransition(session.variant(), session.state(),
                SpeakingExamState.EVALUATION_QUEUED);
        long nextVersion = session.stateVersion() + 1;
        int moved = jdbc.update("""
                update public.speaking_exam_sessions set state='EVALUATION_QUEUED', state_version=?,
                  completed_at=now(), updated_at=now()
                where id=? and state='CLOSING' and state_version=?
                """, nextVersion, session.id(), session.stateVersion());
        if (moved != 1) throw stateConflict();
        finalizeAttempt(session.attemptId(), studentId);
        evaluationService.registerAttempt(session.attemptId(), studentId);
        if (evaluationService.isAiEligible(studentId)) {
            evaluationService.requestAi(session.attemptId(), studentId);
        }
        return response(loadSession(sessionId, studentId, false));
    }

    public SpeakingExaminerSpeechService.AudioContent promptAudio(UUID sessionId, UUID turnId, UUID studentId) {
        requireEnabled();
        return speechService.openPrompt(sessionId, turnId, studentId);
    }

    private void move(SessionRow session, long expectedVersion, SpeakingExamState target) {
        if (session.stateVersion() != expectedVersion) throw stateConflict();
        SpeakingExamStateMachine.requireTransition(session.variant(), session.state(), target);
        boolean clearPrepDeadline = session.state() == SpeakingExamState.PART_2_PREPARATION
                && target != SpeakingExamState.PART_2_PREPARATION;
        int moved = jdbc.update("""
                update public.speaking_exam_sessions set state=?, state_version=state_version+1,
                  part_2_prep_deadline_at=case when ? then null else part_2_prep_deadline_at end,
                  updated_at=now()
                where id=? and state=? and state_version=?
                """, target.name(), clearPrepDeadline, session.id(), session.state().name(), expectedVersion);
        if (moved != 1) throw stateConflict();
    }

    private SpeakingExamState nextState(SessionRow session, TurnRow current) {
        return switch (session.variant()) {
            case FULL_MOCK -> switch (session.state()) {
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
            case PART_1 -> switch (session.state()) {
                case MIC_CHECK -> SpeakingExamState.PART_1;
                case PART_1 -> hasPendingTurn(session.id(), SpeakingExamState.PART_1, current.id())
                        ? SpeakingExamState.PART_1 : SpeakingExamState.CLOSING;
                case CLOSING -> SpeakingExamState.EVALUATION_QUEUED;
                default -> throw stateConflict();
            };
            case PART_2 -> switch (session.state()) {
                case MIC_CHECK -> SpeakingExamState.PART_2_PREPARATION;
                case PART_2_LONG_TURN -> SpeakingExamState.PART_2_FOLLOW_UP;
                case PART_2_FOLLOW_UP -> SpeakingExamState.CLOSING;
                case CLOSING -> SpeakingExamState.EVALUATION_QUEUED;
                default -> throw stateConflict();
            };
            case PART_3 -> switch (session.state()) {
                case MIC_CHECK -> SpeakingExamState.PART_3;
                case PART_3 -> hasPendingTurn(session.id(), SpeakingExamState.PART_3, current.id())
                        ? SpeakingExamState.PART_3 : SpeakingExamState.CLOSING;
                case CLOSING -> SpeakingExamState.EVALUATION_QUEUED;
                default -> throw stateConflict();
            };
        };
    }

    private boolean isAnswerState(SpeakingExamState state) {
        return switch (state) {
            case MIC_CHECK, IDENTITY_CHECK, PART_1, PART_2_LONG_TURN, PART_2_FOLLOW_UP, PART_3 -> true;
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
        int policyFollowUpCap = adaptiveFollowUpEnabled ? Math.max(0, maxAdaptiveFollowUps) : 0;
        return new SpeakingExaminerSessionResponse(session.id(), session.attemptId(), SpeakingExamMode.AI_EXAMINER,
                session.variant(), session.variant().partsIncluded(), session.variant().overallBandAvailable(),
                session.state(), session.stateVersion(), session.adaptiveFollowUpsUsed(),
                new SpeakingExaminerSessionResponse.Policy(policyFollowUpCap, false, true,
                        SpeakingExaminerSessionResponse.ResultType.ESTIMATED_BAND),
                new SpeakingExaminerSessionResponse.Versions(session.promptVersion(), session.rubricVersion(),
                        session.speechModelVersion(), session.featureExtractorVersion()),
                currentTurnResponse(session.id(), session.state()), session.startedAt(), session.completedAt(),
                session.part2PrepDeadlineAt(), session.part2PrepRemainingSeconds());
    }

    private SpeakingExaminerTurnResponse currentTurnResponse(UUID sessionId, SpeakingExamState state) {
        if (state == SpeakingExamState.PART_2_PREPARATION) {
            return turn(currentTurn(sessionId, SpeakingExamState.PART_2_LONG_TURN));
        }
        return isAnswerState(state) || state == SpeakingExamState.CLOSING
                ? turn(currentTurn(sessionId, state)) : null;
    }

    private UUID insertTurn(UUID sessionId, int sequenceNo, SpeakingExaminerQuestionPlanner.PlannedTurn planned) {
        UUID id = UUID.randomUUID();
        var audio = speechService.promptAudio(planned.speechText());
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

    private SpeakingAdaptiveFollowUpService.Context buildAdaptiveContext(SessionRow session, TurnRow current) {
        if (!adaptiveFollowUpEnabled || maxAdaptiveFollowUps <= 0 || session.state() != SpeakingExamState.PART_3) {
            return null;
        }
        int remaining = maxAdaptiveFollowUps - session.adaptiveFollowUpsUsed();
        if (remaining <= 0 || !sequenceSlotFree(session.id(), current.sequenceNo() + 1)) {
            return null;
        }
        String part2CuePrompt = null;
        String part2Transcript = null;
        var part2Rows = jdbc.query("""
                select prompt_text, transcript from public.speaking_exam_turns
                where session_id=? and exam_state='PART_2_LONG_TURN'
                order by sequence_no limit 1
                """, (rs, ignored) -> new String[]{rs.getString("prompt_text"), rs.getString("transcript")},
                session.id());
        if (!part2Rows.isEmpty()) {
            part2CuePrompt = part2Rows.getFirst()[0];
            part2Transcript = part2Rows.getFirst()[1];
        }
        List<SpeakingAdaptiveFollowUpService.Exchange> recentPart3 = new ArrayList<>(jdbc.query("""
                select prompt_text, transcript from public.speaking_exam_turns
                where session_id=? and exam_state='PART_3' and submitted_at is not null
                  and coalesce(transcript, '') <> ''
                order by sequence_no desc limit 3
                """, (rs, ignored) -> new SpeakingAdaptiveFollowUpService.Exchange(
                rs.getString("prompt_text"), rs.getString("transcript")), session.id()));
        Collections.reverse(recentPart3);
        List<String> existingPrompts = jdbc.queryForList("""
                select prompt_text from public.speaking_exam_turns
                where session_id=? and exam_state in ('PART_2_LONG_TURN', 'PART_2_FOLLOW_UP', 'PART_3')
                order by sequence_no
                """, String.class, session.id());
        return new SpeakingAdaptiveFollowUpService.Context(
                current.topicKey(),
                current.promptText(),
                "",
                part2CuePrompt,
                part2Transcript,
                recentPart3,
                existingPrompts,
                remaining);
    }

    private boolean sequenceSlotFree(UUID sessionId, int sequenceNo) {
        Integer count = jdbc.queryForObject("""
                select count(*) from public.speaking_exam_turns where session_id=? and sequence_no=?
                """, Integer.class, sessionId, sequenceNo);
        return count == null || count == 0;
    }

    private void insertAdaptiveTurn(UUID sessionId,
                                    int sequenceNo,
                                    TurnRow parent,
                                    String promptText,
                                    String audioCacheKey) {
        int answerSeconds = parent.answerTimeSeconds() != null && parent.answerTimeSeconds() > 0
                ? parent.answerTimeSeconds() : 45;
        jdbc.update("""
                insert into public.speaking_exam_turns(
                  id, session_id, sequence_no, exam_state, part_no, kind, topic_key, prompt_text,
                  prompt_audio_cache_key, preparation_time_seconds, answer_time_seconds,
                  is_adaptive_follow_up, parent_turn_id)
                values (?, ?, ?, 'PART_3', 3, 'FOLLOW_UP', ?, ?, ?, null, ?, true, ?)
                """, UUID.randomUUID(), sessionId, sequenceNo, parent.topicKey(), promptText,
                audioCacheKey, answerSeconds, parent.id());
    }

    private String sessionSelect() {
        return """
                select exam_session.id, exam_session.attempt_id, exam_session.state, exam_session.state_version,
                  exam_session.variant, exam_session.adaptive_follow_ups_used,
                  exam_session.part_2_prep_deadline_at,
                  case when exam_session.part_2_prep_deadline_at is null then null
                    else greatest(0, ceil(extract(epoch from (exam_session.part_2_prep_deadline_at - now()))))::int
                  end part_2_prep_remaining_seconds,
                  exam_session.prompt_version, exam_session.rubric_version, exam_session.speech_model_version,
                  exam_session.feature_extractor_version, exam_session.started_at, exam_session.completed_at,
                  attempt.status::text attempt_status
                from public.speaking_exam_sessions exam_session
                join public.test_attempts attempt on attempt.id=exam_session.attempt_id
                """;
    }

    private SessionRow mapSession(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        return new SessionRow(rs.getObject("id", UUID.class), rs.getObject("attempt_id", UUID.class),
                SpeakingSessionVariant.valueOf(rs.getString("variant")),
                SpeakingExamState.valueOf(rs.getString("state")), rs.getLong("state_version"),
                rs.getInt("adaptive_follow_ups_used"),
                rs.getObject("part_2_prep_deadline_at", OffsetDateTime.class),
                rs.getObject("part_2_prep_remaining_seconds", Integer.class),
                rs.getString("prompt_version"), rs.getString("rubric_version"),
                rs.getString("speech_model_version"), rs.getString("feature_extractor_version"),
                rs.getObject("started_at", OffsetDateTime.class), rs.getObject("completed_at", OffsetDateTime.class),
                rs.getString("attempt_status"));
    }

    private TurnRow currentTurn(UUID sessionId, SpeakingExamState state) {
        return currentTurnExcluding(sessionId, state, null);
    }

    private TurnRow currentTurnExcluding(UUID sessionId, SpeakingExamState state, UUID excludeTurnId) {
        var values = excludeTurnId == null
                ? jdbc.query("""
                        select id, session_id, sequence_no, exam_state, part_no, kind, topic_key, prompt_text,
                          prompt_audio_cache_key, preparation_time_seconds, answer_time_seconds, is_adaptive_follow_up
                        from public.speaking_exam_turns
                        where session_id=? and exam_state=? and submitted_at is null order by sequence_no limit 1
                        """, this::mapTurn, sessionId, state.name())
                : jdbc.query("""
                        select id, session_id, sequence_no, exam_state, part_no, kind, topic_key, prompt_text,
                          prompt_audio_cache_key, preparation_time_seconds, answer_time_seconds, is_adaptive_follow_up
                        from public.speaking_exam_turns
                        where session_id=? and exam_state=? and submitted_at is null and id<>?
                        order by sequence_no limit 1
                        """, this::mapTurn, sessionId, state.name(), excludeTurnId);
        return values.isEmpty() ? null : values.getFirst();
    }

    private TurnRow nextTurn(UUID sessionId, SpeakingExamState state, UUID completedTurnId) {
        if (state == SpeakingExamState.PART_2_PREPARATION) {
            return currentTurnExcluding(sessionId, SpeakingExamState.PART_2_LONG_TURN, completedTurnId);
        }
        return (isAnswerState(state) || state == SpeakingExamState.CLOSING)
                ? currentTurnExcluding(sessionId, state, completedTurnId) : null;
    }

    private SpeakingExaminerTurnResponse turn(TurnRow value) {
        if (value == null) return null;
        return new SpeakingExaminerTurnResponse(value.id(), value.sequenceNo(), value.partNo(), value.kind(),
                value.topicKey(), value.promptText(), SpeakingExaminerSpeechService.audioPath(value.sessionId(), value.id()),
                value.preparationTimeSeconds(), value.answerTimeSeconds(), value.adaptiveFollowUp());
    }

    private TurnRow mapTurn(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        return new TurnRow(rs.getObject("id", UUID.class), rs.getObject("session_id", UUID.class),
                rs.getInt("sequence_no"), SpeakingExamState.valueOf(rs.getString("exam_state")),
                rs.getObject("part_no", Integer.class), SpeakingExaminerTurnResponse.Kind.valueOf(rs.getString("kind")),
                rs.getString("topic_key"), rs.getString("prompt_text"), rs.getString("prompt_audio_cache_key"),
                rs.getObject("preparation_time_seconds", Integer.class),
                rs.getObject("answer_time_seconds", Integer.class),
                rs.getBoolean("is_adaptive_follow_up"));
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
                accepted.stateVersion(), session.adaptiveFollowUpsUsed(),
                completed ? null : turn(loadTurn(accepted.nextTurnId())), completed, null,
                session.part2PrepDeadlineAt(), session.part2PrepRemainingSeconds());
    }

    private TurnRow loadTurn(UUID turnId) {
        if (turnId == null) return null;
        var values = jdbc.query("""
                select id, session_id, sequence_no, exam_state, part_no, kind, topic_key, prompt_text,
                  prompt_audio_cache_key, preparation_time_seconds, answer_time_seconds, is_adaptive_follow_up
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

    private CodedApiException variantConflict(SpeakingSessionVariant persistedVariant) {
        return new CodedApiException(HttpStatus.CONFLICT, "SPEAKING_EXAMINER_VARIANT_CONFLICT",
                "Phiên Speaking đã được tạo với chế độ " + persistedVariant + "; không thể đổi chế độ khi tiếp tục");
    }

    public record TurnPreparation(SpeakingExaminerTurnSubmissionResponse alreadyAccepted,
                                  int durationMs,
                                  SpeakingAdaptiveFollowUpService.Context adaptiveContext) {
        static TurnPreparation accepted(SpeakingExaminerTurnSubmissionResponse response) {
            return new TurnPreparation(response, 0, null);
        }
    }

    private record AttemptRow(String status, String builderContent) {}

    private record SessionRow(UUID id, UUID attemptId, SpeakingSessionVariant variant,
                              SpeakingExamState state, long stateVersion, int adaptiveFollowUpsUsed,
                              OffsetDateTime part2PrepDeadlineAt, Integer part2PrepRemainingSeconds,
                              String promptVersion, String rubricVersion, String speechModelVersion,
                              String featureExtractorVersion, OffsetDateTime startedAt, OffsetDateTime completedAt,
                              String attemptStatus) {}

    private record TurnRow(UUID id, UUID sessionId, int sequenceNo, SpeakingExamState state, Integer partNo,
                           SpeakingExaminerTurnResponse.Kind kind, String topicKey, String promptText,
                           String promptAudioCacheKey, Integer preparationTimeSeconds, Integer answerTimeSeconds,
                           boolean adaptiveFollowUp) {}

    private record AcceptedTurn(UUID studentTurnId, SpeakingExamState state, long stateVersion,
                                UUID nextTurnId) {}
}
