package com.theieltsspells.testing.application;

import com.theieltsspells.shared.ai.AiGenerationResult;
import com.theieltsspells.shared.ai.AiProvider;
import com.theieltsspells.shared.ai.AiProviderRouter;
import com.theieltsspells.shared.ai.AiResultDecoder;
import com.theieltsspells.shared.ai.AiTaskType;
import com.theieltsspells.shared.application.CodedApiException;
import com.theieltsspells.testing.application.dto.SpeakingExamPartTwoReadyRequest;
import com.theieltsspells.testing.application.dto.SpeakingExaminerTurnResponse;
import com.theieltsspells.testing.application.dto.SubmitSpeakingExaminerTurnRequest;
import com.theieltsspells.testing.domain.SpeakingExamState;
import com.theieltsspells.testing.domain.SpeakingSessionVariant;
import com.theieltsspells.testing.infrastructure.speech.SpeachesSpeechClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "app.speaking-examiner.enabled=true",
        "app.speaking-examiner.part-2-enabled=true",
        "app.speaking-examiner.part-3-enabled=true",
        "app.speaking-examiner.transcription-initial-delay-millis=3600000",
        "app.writing-evaluation.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SpeakingExamSessionIntegrationTests {
    private static final String CACHE_KEY = "a".repeat(64);
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("speaking_exam_session")
            .withUsername("speaking_exam_test")
            .withPassword("speaking_exam_test");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired SpeakingExamSessionService sessions;
    @Autowired SpeakingTurnService turns;
    @Autowired SpeakingBackgroundTranscriptionWorker transcriptionWorker;
    @Autowired StudentSpeakingDeliveryService delivery;
    @Autowired JdbcTemplate jdbc;
    @MockBean SpeakingAudioService audioService;
    @MockBean SpeakingExaminerSpeechService speechService;
    @MockBean AiProviderRouter aiProviderRouter;

    private UUID studentId;
    private UUID attemptId;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
        attemptId = UUID.randomUUID();
        when(speechService.modelVersion()).thenReturn("speaches:test");
        when(speechService.promptAudio(anyString())).thenReturn(
                new SpeakingExaminerSpeechService.PromptAudio(CACHE_KEY, "speaking-examiner/tts/test.wav", "audio/wav", 1));
        when(speechService.transcribeTimed(anyString())).thenReturn(
                new SpeakingExaminerSpeechService.TimedTranscription(
                        new SpeachesSpeechClient.Transcription("A recorded answer.", "en", 1_000, List.of(), null),
                        2, 100));
        when(audioService.durationMsForOwnedStorageKey(any(), any(), anyString(), anyString())).thenReturn(1_000);
        seedAttempt();
    }

    @Test
    void resumesThePersistedPartTwoRouteAfterRefresh() {
        var session = sessions.createOrResume(attemptId, studentId);
        assertThat(session.variant()).isEqualTo(SpeakingSessionVariant.FULL_MOCK);
        assertThat(session.partsIncluded()).containsExactly(1, 2, 3);
        assertThat(session.overallBandAvailable()).isTrue();
        for (int index = 0; index < 5; index++) session = submitCurrent(session);

        assertThat(session.state()).isEqualTo(SpeakingExamState.PART_2_PREPARATION);
        assertThat(session.part2PrepDeadlineAt()).isNotNull();
        assertThat(session.part2PrepRemainingSeconds()).isNotNull().isBetween(1, 60);
        var refreshed = sessions.get(session.sessionId(), studentId);
        assertThat(refreshed.stateVersion()).isEqualTo(session.stateVersion());
        assertThat(refreshed.currentTurn().turnId()).isEqualTo(session.currentTurn().turnId());
        assertThat(refreshed.currentTurn().topicKey()).isEqualTo("memorable-place");
        assertThat(refreshed.part2PrepDeadlineAt()).isEqualTo(session.part2PrepDeadlineAt());
        assertThat(refreshed.part2PrepRemainingSeconds()).isLessThanOrEqualTo(session.part2PrepRemainingSeconds());

        var ready = sessions.markPartTwoReady(session.sessionId(),
                new SpeakingExamPartTwoReadyRequest(session.stateVersion()), studentId);
        assertThat(ready.state()).isEqualTo(SpeakingExamState.PART_2_LONG_TURN);
        assertThat(ready.part2PrepDeadlineAt()).isNull();
        assertThat(ready.part2PrepRemainingSeconds()).isNull();
        assertThat(sessions.get(session.sessionId(), studentId).currentTurn().turnId())
                .isEqualTo(ready.currentTurn().turnId());
    }

    @Test
    void generatesBoundedPartThreeAdaptiveFollowUpsAndReplaysDeterministicallyOnRetry() {
        when(speechService.transcribeTimed(anyString())).thenReturn(
                new SpeakingExaminerSpeechService.TimedTranscription(
                        new SpeachesSpeechClient.Transcription(
                                "Cities transform mainly because commercial towers replace old neighborhood parks.",
                                "en", 2_000, List.of(), null),
                        2, 100));
        AtomicInteger aiCalls = new AtomicInteger();
        List<String> generatedQuestions = List.of(
                "How does losing neighborhood parks affect older residents in cities?",
                "Should urban planners require green spaces inside every commercial district?",
                "This third adaptive prompt must never be used because quota is two?"
        );
        when(aiProviderRouter.execute(eq(AiTaskType.CHAT), any(), any()))
                .thenAnswer(invocation -> {
                    int callIdx = Math.min(aiCalls.getAndIncrement(), generatedQuestions.size() - 1);
                    @SuppressWarnings("unchecked")
                    AiResultDecoder<?> decoder = invocation.getArgument(2);
                    return decoder.decode(new AiGenerationResult(
                            AiProvider.GEMINI,
                            "gemini-2.5-flash",
                            "{\"shouldFollowUp\":true,\"questionText\":\""
                                    + generatedQuestions.get(callIdx)
                                    + "\",\"rationale\":\"follow-up\"}"));
                });

        var session = sessions.createOrResume(attemptId, studentId, SpeakingSessionVariant.PART_3);
        assertThat(session.policy().maxAdaptiveFollowUps()).isEqualTo(2);
        var micResult = turns.submit(session.sessionId(), requestFor(session, UUID.randomUUID()), studentId);
        assertThat(micResult.nextTurn().promptText()).isEqualTo("Why do places change?");
        session = sessions.get(session.sessionId(), studentId);

        UUID retryClientTurnId = UUID.randomUUID();
        var part3FirstRequest = requestFor(session, retryClientTurnId);
        var firstSubmission = turns.submit(session.sessionId(), part3FirstRequest, studentId);
        assertThat(firstSubmission.adaptiveFollowUpsUsed()).isEqualTo(1);
        assertThat(firstSubmission.nextTurn().adaptiveFollowUp()).isTrue();
        assertThat(firstSubmission.nextTurn().kind()).isEqualTo(SpeakingExaminerTurnResponse.Kind.FOLLOW_UP);
        assertThat(firstSubmission.nextTurn().promptText()).isEqualTo(generatedQuestions.getFirst());

        var retrySubmission = turns.submit(session.sessionId(), part3FirstRequest, studentId);
        assertThat(retrySubmission.nextTurn().turnId()).isEqualTo(firstSubmission.nextTurn().turnId());
        assertThat(retrySubmission.nextTurn().promptText()).isEqualTo(generatedQuestions.getFirst());
        verify(aiProviderRouter, times(1)).execute(eq(AiTaskType.CHAT), any(), any());

        session = sessions.get(session.sessionId(), studentId);
        var secondSubmission = turns.submit(session.sessionId(), requestFor(session, UUID.randomUUID()), studentId);
        assertThat(secondSubmission.adaptiveFollowUpsUsed()).isEqualTo(2);
        assertThat(secondSubmission.nextTurn().adaptiveFollowUp()).isTrue();
        assertThat(secondSubmission.nextTurn().promptText()).isEqualTo(generatedQuestions.get(1));

        session = sessions.get(session.sessionId(), studentId);
        var afterQuotaSubmission = turns.submit(session.sessionId(), requestFor(session, UUID.randomUUID()), studentId);
        assertThat(afterQuotaSubmission.adaptiveFollowUpsUsed()).isEqualTo(2);
        assertThat(afterQuotaSubmission.nextTurn().adaptiveFollowUp()).isFalse();
        assertThat(afterQuotaSubmission.nextTurn().promptText()).isEqualTo("How can cities preserve identity?");
        verify(aiProviderRouter, times(2)).execute(eq(AiTaskType.CHAT), any(), any());
    }

    @Test
    void fallsBackToBankedRouteWhenAdaptiveFollowUpRouterFails() {
        when(speechService.transcribeTimed(anyString())).thenReturn(
                new SpeakingExaminerSpeechService.TimedTranscription(
                        new SpeachesSpeechClient.Transcription(
                                "Public parks give communities space to exercise and socialize every week.",
                                "en", 2_000, List.of(), null),
                        2, 100));
        when(aiProviderRouter.execute(eq(AiTaskType.CHAT), any(), any()))
                .thenThrow(new RuntimeException("Simulated provider outage"));

        var session = sessions.createOrResume(attemptId, studentId, SpeakingSessionVariant.PART_3);
        turns.submit(session.sessionId(), requestFor(session, UUID.randomUUID()), studentId);
        session = sessions.get(session.sessionId(), studentId);

        var submission = turns.submit(session.sessionId(), requestFor(session, UUID.randomUUID()), studentId);
        assertThat(submission.state()).isEqualTo(SpeakingExamState.PART_3);
        assertThat(submission.adaptiveFollowUpsUsed()).isZero();
        assertThat(submission.nextTurn().adaptiveFollowUp()).isFalse();
        assertThat(submission.nextTurn().promptText()).isEqualTo("How can cities preserve identity?");
    }

    @Test
    void persistsAndReturnsTheRequestedPartVariant() {
        var session = sessions.createOrResume(attemptId, studentId, SpeakingSessionVariant.PART_1);

        assertThat(session.variant()).isEqualTo(SpeakingSessionVariant.PART_1);
        assertThat(session.partsIncluded()).containsExactly(1);
        assertThat(session.overallBandAvailable()).isFalse();
        assertThat(jdbc.queryForObject("select variant from public.speaking_exam_sessions where id=?",
                String.class, session.sessionId())).isEqualTo("PART_1");

        assertThatThrownBy(() -> sessions.createOrResume(attemptId, studentId, SpeakingSessionVariant.FULL_MOCK))
                .isInstanceOfSatisfying(CodedApiException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(409);
                    assertThat(exception.code()).isEqualTo("SPEAKING_EXAMINER_VARIANT_CONFLICT");
                });

        var resumed = sessions.createOrResume(attemptId, studentId, SpeakingSessionVariant.PART_1);
        assertThat(resumed.sessionId()).isEqualTo(session.sessionId());
        assertThat(resumed.variant()).isEqualTo(SpeakingSessionVariant.PART_1);
        assertThat(jdbc.queryForList("""
                select prompt_text from public.speaking_exam_turns
                where session_id=? and exam_state='PART_1'
                order by sequence_no
                """, String.class, session.sessionId()))
                .containsExactly("Where do you live?", "What do you enjoy at home?", "Would you move one day?");
    }

    @Test
    void completesAStandalonePartOneWithoutCreatingOtherParts() {
        var session = sessions.createOrResume(attemptId, studentId, SpeakingSessionVariant.PART_1);
        session = submitCurrent(session);
        session = submitCurrent(session);

        var refreshed = sessions.get(session.sessionId(), studentId);
        assertThat(refreshed.state()).isEqualTo(SpeakingExamState.PART_1);
        assertThat(refreshed.currentTurn().turnId()).isEqualTo(session.currentTurn().turnId());

        session = submitCurrent(refreshed);
        var closingSubmission = turns.submit(session.sessionId(), requestFor(session, UUID.randomUUID()), studentId);
        assertThat(closingSubmission.state()).isEqualTo(SpeakingExamState.CLOSING);
        assertThat(closingSubmission.nextTurn()).isNotNull();
        assertThat(closingSubmission.nextTurn().kind()).isEqualTo(SpeakingExaminerTurnResponse.Kind.CLOSING);
        session = sessions.get(session.sessionId(), studentId);
        assertThat(session.state()).isEqualTo(SpeakingExamState.CLOSING);
        var closingSession = session;
        assertThatThrownBy(() -> sessions.submitTurn(closingSession.sessionId(),
                requestFor(closingSession, UUID.randomUUID()), studentId))
                .isInstanceOfSatisfying(CodedApiException.class,
                        exception -> assertThat(exception.status().value()).isEqualTo(409));
        var closingVersion = session.stateVersion();
        session = sessions.completeClosing(session.sessionId(),
                new com.theieltsspells.testing.application.dto.CompleteSpeakingExamSessionRequest(closingVersion),
                studentId);
        var completedReplay = sessions.completeClosing(session.sessionId(),
                new com.theieltsspells.testing.application.dto.CompleteSpeakingExamSessionRequest(closingVersion),
                studentId);

        assertThat(session.state()).isEqualTo(SpeakingExamState.EVALUATION_QUEUED);
        assertThat(completedReplay.stateVersion()).isEqualTo(session.stateVersion());
        assertThat(session.completedAt()).isNotNull();
        assertThat(jdbc.queryForList("""
                select distinct exam_state from public.speaking_exam_turns
                where session_id=? order by exam_state
                """, String.class, session.sessionId()))
                .containsExactly("CLOSING", "MIC_CHECK", "PART_1");
        assertThat(jdbc.queryForObject("""
                select count(*) from public.speaking_exam_turns
                where session_id=? and part_no=1 and submitted_at is not null
                """, Integer.class, session.sessionId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                select count(*) from public.speaking_exam_turns
                where session_id=? and exam_state='CLOSING' and submitted_at is null
                """, Integer.class, session.sessionId())).isEqualTo(1);
        var result = delivery.getResult(attemptId, studentId);
        assertThat(result.variant()).isEqualTo(SpeakingSessionVariant.PART_1);
        assertThat(result.partsEvaluated()).containsExactly(1);
        assertThat(result.overallBandAvailable()).isFalse();
        assertThat(result.aiEligible()).isFalse();
        assertThat(result.parts()).extracting(com.theieltsspells.testing.application.dto.SpeakingPartResponse::partNo)
                .containsExactly(1);
        assertThat(result.responses()).hasSize(3);
        assertThat(result.evaluations()).singleElement().satisfies(evaluation -> {
            assertThat(evaluation.status()).isEqualTo("NOT_ELIGIBLE");
            assertThat(evaluation.criteria()).isEmpty();
            assertThat(evaluation.variant()).isEqualTo(SpeakingSessionVariant.PART_1);
            assertThat(evaluation.overallBand()).isNull();
            assertThat(evaluation.overallBandAvailable()).isFalse();
        });
    }

    @Test
    void recordsLatencyBreakdownAndEndReasonTelemetry() {
        var session = sessions.createOrResume(attemptId, studentId, SpeakingSessionVariant.PART_1);
        session = submitCurrent(session);

        var baseRequest = requestFor(session, UUID.randomUUID());
        var submission = sessions.submitTurn(session.sessionId(), new SubmitSpeakingExaminerTurnRequest(
                baseRequest.clientTurnId(),
                baseRequest.examinerTurnId(),
                baseRequest.stateVersion(),
                baseRequest.storageKey(),
                baseRequest.transcript(),
                baseRequest.durationMs(),
                com.theieltsspells.testing.application.dto.SpeakingTurnEndReason.VAD_SILENCE,
                142
        ), studentId);

        assertThat(submission.latency()).isNotNull();
        assertThat(submission.latency().storageReadMs()).isEqualTo(2);
        assertThat(submission.latency().sttProcessingMs()).isEqualTo(100);
        assertThat(submission.latency().followUpGenerationMs()).isZero();
        assertThat(submission.latency().ttsProcessingMs()).isZero();
        assertThat(submission.latency().serverTurnMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void returnsTheNextBankedQuestionBeforeBackgroundTranscriptionCompletes() {
        var session = sessions.createOrResume(attemptId, studentId, SpeakingSessionVariant.PART_1);
        clearInvocations(speechService);

        var microphoneCheck = turns.submit(
                session.sessionId(), requestFor(session, UUID.randomUUID()), studentId);
        assertThat(microphoneCheck.nextTurn().promptText()).isEqualTo("Where do you live?");
        verify(speechService, never()).transcribeTimed(anyString());

        session = sessions.get(session.sessionId(), studentId);
        UUID answeredTurnId = session.currentTurn().turnId();
        var submission = turns.submit(
                session.sessionId(), requestFor(session, UUID.randomUUID()), studentId);

        assertThat(submission.nextTurn().promptText()).isEqualTo("What do you enjoy at home?");
        assertThat(submission.latency().storageReadMs()).isZero();
        assertThat(submission.latency().sttProcessingMs()).isZero();
        assertThat(jdbc.queryForObject("""
                select transcription_status from public.speaking_exam_turns where id=?
                """, String.class, answeredTurnId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("""
                select transcript is null from public.speaking_exam_turns where id=?
                """, Boolean.class, answeredTurnId)).isTrue();
        verify(speechService, never()).transcribeTimed(anyString());

        for (int processed = 0; processed < 20 && "PENDING".equals(jdbc.queryForObject("""
                select transcription_status from public.speaking_exam_turns where id=?
                """, String.class, answeredTurnId)); processed++) {
            transcriptionWorker.processNext();
        }

        assertThat(jdbc.queryForObject("""
                select transcription_status from public.speaking_exam_turns where id=?
                """, String.class, answeredTurnId)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("""
                select transcript from public.speaking_exam_turns where id=?
                """, String.class, answeredTurnId)).isEqualTo("A recorded answer.");
        verify(speechService, atLeastOnce()).transcribeTimed(anyString());
    }

    @Test
    void completesAStandalonePartTwoThroughPreparationAndFollowUp() {
        var session = sessions.createOrResume(attemptId, studentId, SpeakingSessionVariant.PART_2);
        session = submitCurrent(session);
        assertThat(session.state()).isEqualTo(SpeakingExamState.PART_2_PREPARATION);
        var preparation = sessions.get(session.sessionId(), studentId);
        assertThat(preparation.stateVersion()).isEqualTo(session.stateVersion());
        assertThat(preparation.currentTurn()).isEqualTo(session.currentTurn());
        assertThat(preparation.currentTurn().preparationTimeSeconds()).isEqualTo(60);

        var readyRequest = new SpeakingExamPartTwoReadyRequest(preparation.stateVersion());
        session = sessions.markPartTwoReady(session.sessionId(), readyRequest, studentId);
        var replayedReady = sessions.markPartTwoReady(session.sessionId(), readyRequest, studentId);
        assertThat(replayedReady.state()).isEqualTo(SpeakingExamState.PART_2_LONG_TURN);
        assertThat(replayedReady.stateVersion()).isEqualTo(session.stateVersion());
        assertThat(replayedReady.currentTurn()).isEqualTo(session.currentTurn());
        session = submitCurrent(session);
        session = submitCurrent(session);
        session = sessions.completeClosing(session.sessionId(),
                new com.theieltsspells.testing.application.dto.CompleteSpeakingExamSessionRequest(
                        session.stateVersion()), studentId);

        assertThat(session.state()).isEqualTo(SpeakingExamState.EVALUATION_QUEUED);
        assertThat(jdbc.queryForList("""
                select distinct exam_state from public.speaking_exam_turns
                where session_id=? order by exam_state
                """, String.class, session.sessionId()))
                .containsExactly("CLOSING", "MIC_CHECK", "PART_2_FOLLOW_UP", "PART_2_LONG_TURN");
        var result = delivery.getResult(attemptId, studentId);
        assertThat(result.variant()).isEqualTo(SpeakingSessionVariant.PART_2);
        assertThat(result.partsEvaluated()).containsExactly(2);
        assertThat(result.overallBandAvailable()).isFalse();
        assertThat(result.parts()).extracting(com.theieltsspells.testing.application.dto.SpeakingPartResponse::partNo)
                .containsExactly(2);
        assertThat(result.parts().getFirst().questions())
                .extracting(com.theieltsspells.testing.application.dto.SpeakingQuestionResponse::questionKey)
                .containsExactlyElementsOf(result.responses().stream()
                        .limit(2)
                        .map(com.theieltsspells.testing.application.dto.SpeakingSavedResponse::questionKey)
                        .toList());
        assertThat(result.parts().getFirst().questions())
                .extracting(com.theieltsspells.testing.application.dto.SpeakingQuestionResponse::promptText)
                .containsExactly(
                        "Describe a memorable place.",
                        "Why was it memorable?"
                );
        assertThat(result.evaluations()).singleElement().satisfies(evaluation -> {
            assertThat(evaluation.variant()).isEqualTo(SpeakingSessionVariant.PART_2);
            assertThat(evaluation.partsEvaluated()).containsExactly(2);
            assertThat(evaluation.overallBand()).isNull();
            assertThat(evaluation.overallBandAvailable()).isFalse();
        });
    }

    @Test
    void completesAStandalonePartThreeWithoutEarlierParts() {
        var session = sessions.createOrResume(attemptId, studentId, SpeakingSessionVariant.PART_3);

        for (int index = 0; index < 4; index++) session = submitCurrent(session);
        session = sessions.completeClosing(session.sessionId(),
                new com.theieltsspells.testing.application.dto.CompleteSpeakingExamSessionRequest(
                        session.stateVersion()), studentId);

        assertThat(session.state()).isEqualTo(SpeakingExamState.EVALUATION_QUEUED);
        assertThat(jdbc.queryForList("""
                select distinct exam_state from public.speaking_exam_turns
                where session_id=? order by exam_state
                """, String.class, session.sessionId()))
                .containsExactly("CLOSING", "MIC_CHECK", "PART_3");
        var result = delivery.getResult(attemptId, studentId);
        assertThat(result.variant()).isEqualTo(SpeakingSessionVariant.PART_3);
        assertThat(result.partsEvaluated()).containsExactly(3);
        assertThat(result.overallBandAvailable()).isFalse();
        assertThat(result.parts()).extracting(com.theieltsspells.testing.application.dto.SpeakingPartResponse::partNo)
                .containsExactly(3);
        assertThat(result.evaluations()).singleElement().satisfies(evaluation -> {
            assertThat(evaluation.variant()).isEqualTo(SpeakingSessionVariant.PART_3);
            assertThat(evaluation.partsEvaluated()).containsExactly(3);
            assertThat(evaluation.overallBand()).isNull();
            assertThat(evaluation.overallBandAvailable()).isFalse();
        });
    }

    @Test
    void completesTheFullRouteAndQueuesEvaluation() {
        var session = sessions.createOrResume(attemptId, studentId);
        for (int index = 0; index < 5; index++) session = submitCurrent(session);

        session = sessions.markPartTwoReady(session.sessionId(),
                new SpeakingExamPartTwoReadyRequest(session.stateVersion()), studentId);
        session = submitCurrent(session);
        session = submitCurrent(session);
        session = submitCurrent(session);
        session = submitCurrent(session);
        session = submitCurrent(session);
        session = sessions.completeClosing(session.sessionId(),
                new com.theieltsspells.testing.application.dto.CompleteSpeakingExamSessionRequest(
                        session.stateVersion()), studentId);

        assertThat(session.state()).isEqualTo(SpeakingExamState.EVALUATION_QUEUED);
        assertThat(session.currentTurn()).isNull();
        assertThat(session.completedAt()).isNotNull();
        assertThat(jdbc.queryForObject("select status::text from public.test_attempts where id=?",
                String.class, attemptId)).isEqualTo("SUBMITTED");
        assertThat(sessions.get(session.sessionId(), studentId).completedAt()).isNotNull();
    }

    @Test
    void abortMakesTheExaminerSessionTerminalWhileKeepingAttemptRestartable() {
        var session = sessions.createOrResume(attemptId, studentId);

        var aborted = sessions.abort(session.sessionId(),
                new com.theieltsspells.testing.application.dto.AbortSpeakingExamSessionRequest(session.stateVersion()),
                studentId);

        assertThat(aborted.state()).isEqualTo(SpeakingExamState.ABORTED);
        assertThat(aborted.stateVersion()).isEqualTo(session.stateVersion() + 1);
        assertThat(sessions.get(session.sessionId(), studentId).state()).isEqualTo(SpeakingExamState.ABORTED);
        assertThat(jdbc.queryForObject("select status::text from public.test_attempts where id=?",
                String.class, attemptId)).isEqualTo("IN_PROGRESS");
        assertThatThrownBy(() -> sessions.submitTurn(session.sessionId(), requestFor(session, UUID.randomUUID()), studentId))
                .isInstanceOfSatisfying(CodedApiException.class,
                        exception -> assertThat(exception.status().value()).isEqualTo(409));

        var repeatedAbort = sessions.abort(aborted.sessionId(),
                new com.theieltsspells.testing.application.dto.AbortSpeakingExamSessionRequest(0), studentId);
        assertThat(repeatedAbort.state()).isEqualTo(SpeakingExamState.ABORTED);
        assertThat(repeatedAbort.stateVersion()).isEqualTo(aborted.stateVersion());
    }

    @Test
    void startingSelfPracticeAfterAbortExpiresAbortedAttemptAndCreatesFreshAttempt() {
        UUID versionId = jdbc.queryForObject(
                "select test_version_id from public.test_attempts where id = ?", UUID.class, attemptId);
        jdbc.update("""
                update public.tests
                set status = cast('PUBLISHED' as public.publish_status),
                    current_published_version_id = ?
                where id = (select test_id from public.test_versions where id = ?)
                """, versionId, versionId);

        var session = sessions.createOrResume(attemptId, studentId, SpeakingSessionVariant.FULL_MOCK);
        sessions.abort(session.sessionId(),
                new com.theieltsspells.testing.application.dto.AbortSpeakingExamSessionRequest(session.stateVersion()),
                studentId);

        var nextAttempt = delivery.startOrResumeSelfPractice(versionId, studentId, false);
        assertThat(nextAttempt.attemptId()).isNotEqualTo(attemptId);
        assertThat(nextAttempt.status()).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select status::text from public.test_attempts where id = ?",
                String.class, attemptId)).isEqualTo("EXPIRED");

        var nextSession = sessions.createOrResume(nextAttempt.attemptId(), studentId, SpeakingSessionVariant.PART_1);
        assertThat(nextSession.state()).isEqualTo(SpeakingExamState.MIC_CHECK);
        assertThat(nextSession.variant()).isEqualTo(SpeakingSessionVariant.PART_1);
    }

    @Test
    void replaysTheSameClientTurnWithTheOriginalNextTurnAfterTheSessionAdvances() {
        var session = sessions.createOrResume(attemptId, studentId, SpeakingSessionVariant.PART_1);
        var request = requestFor(session, UUID.randomUUID());

        var first = sessions.submitTurn(session.sessionId(), request, studentId);
        var advanced = sessions.get(session.sessionId(), studentId);
        submitCurrent(advanced);
        var replay = sessions.submitTurn(session.sessionId(), request, studentId);

        assertThat(replay.studentTurnId()).isEqualTo(first.studentTurnId());
        assertThat(replay.state()).isEqualTo(first.state());
        assertThat(replay.stateVersion()).isEqualTo(first.stateVersion());
        assertThat(replay.nextTurn()).isEqualTo(first.nextTurn());
        assertThat(jdbc.queryForObject("""
                select count(*) from public.speaking_exam_turns
                where session_id=? and client_turn_id=?
                """, Integer.class, session.sessionId(), request.clientTurnId())).isEqualTo(1);
    }

    @Test
    void acceptsOnlyOneConcurrentRequestForTheSameStateVersion() throws Exception {
        var session = sessions.createOrResume(attemptId, studentId, SpeakingSessionVariant.PART_1);
        var first = requestFor(session, UUID.randomUUID());
        var second = requestFor(session, UUID.randomUUID());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var outcomes = List.of(
                    executor.submit(() -> submitAfter(start, session.sessionId(), first)),
                    executor.submit(() -> submitAfter(start, session.sessionId(), second)));
            start.countDown();
            var results = outcomes.stream().map(this::result).toList();
            assertThat(results).filteredOn(Outcome::accepted).hasSize(1);
            assertThat(results).filteredOn(outcome -> !outcome.accepted())
                    .singleElement().extracting(Outcome::status).isEqualTo(409);
        }
    }

    private Outcome submitAfter(CountDownLatch start, UUID sessionId, SubmitSpeakingExaminerTurnRequest request) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Workers did not start");
            sessions.submitTurn(sessionId, request, studentId);
            return new Outcome(true, 200);
        } catch (CodedApiException exception) {
            return new Outcome(false, exception.status().value());
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private Outcome result(java.util.concurrent.Future<Outcome> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Concurrent turn did not complete", exception);
        }
    }

    private com.theieltsspells.testing.application.dto.SpeakingExaminerSessionResponse submitCurrent(
            com.theieltsspells.testing.application.dto.SpeakingExaminerSessionResponse session) {
        var result = sessions.submitTurn(session.sessionId(), requestFor(session, UUID.randomUUID()), studentId);
        return sessions.get(result.sessionId(), studentId);
    }

    private SubmitSpeakingExaminerTurnRequest requestFor(
            com.theieltsspells.testing.application.dto.SpeakingExaminerSessionResponse session, UUID clientTurnId) {
        String storageKey = "speaking-attempts/" + attemptId + "/" + UUID.randomUUID() + ".webm";
        jdbc.update("""
                insert into public.speaking_audio_assets(
                  attempt_id, student_id, question_key, storage_key, mime_type, size_bytes, duration_ms)
                values (?, ?, ?, ?, 'audio/webm', 1, 1000)
                """, attemptId, studentId, session.currentTurn().turnId().toString(), storageKey);
        return new SubmitSpeakingExaminerTurnRequest(clientTurnId, session.currentTurn().turnId(), session.stateVersion(),
                storageKey, "client transcript is telemetry only", 1_000);
    }

    private void seedAttempt() {
        UUID testId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        jdbc.update("insert into auth.users(id) values (?)", studentId);
        jdbc.update("insert into public.profiles(id, full_name, email) values (?, ?, ?)", studentId,
                "Speaking Student", "speaking-" + studentId + "@example.test");
        jdbc.update("insert into public.student_profiles(user_id, student_code) values (?, ?)", studentId,
                "SP-" + studentId.toString().substring(0, 8));
        jdbc.update("insert into public.tests(id, title, duration_minutes, status, created_by) values (?, ?, 15, cast('DRAFT' as public.publish_status), ?)",
                testId, "Speaking integration", studentId);
        jdbc.update("""
                insert into public.test_versions(
                  id, test_id, version_number, version_label, title, duration_minutes, primary_skill,
                  test_type, tags, builder_content, published_by)
                values (?, ?, 1, 'v1', 'Speaking integration', 15, cast('SPEAKING' as public.skill_type),
                  'SINGLE_SKILL', '[]'::jsonb, cast(? as jsonb), ?)
                """, versionId, testId, builder(), studentId);
        jdbc.update("""
                insert into public.test_attempts(
                  id, student_id, attempt_no, status, test_version_id, attempt_origin)
                values (?, ?, 1, cast('IN_PROGRESS' as public.attempt_status), ?, 'SELF_PRACTICE')
                """, attemptId, studentId, versionId);
        jdbc.update("""
                insert into public.speaking_examiner_tts_assets(
                  cache_key, model, voice, prompt_hash, storage_key, mime_type, size_bytes)
                values (?, 'test', 'test', ?, 'speaking-examiner/tts/test.wav', 'audio/wav', 1)
                on conflict (cache_key) do nothing
                """, CACHE_KEY, CACHE_KEY);
    }

    private String builder() {
        return """
                {"parts":[
                  {"id":"home","partNo":1,"questions":[
                    {"topicKey":"home-town","promptText":"Where do you live?"},
                    {"topicKey":"home-life","promptText":"What do you enjoy at home?"},
                    {"topicKey":"future-home","promptText":"Would you move one day?"},
                    {"topicKey":"ignored-home","promptText":"This fourth question must not be snapshotted."}]},
                  {"id":"memorable-place","partNo":2,"cueCardPromptHtml":"Describe a memorable place.",
                    "followUpQuestions":[{"promptText":"Why was it memorable?"}]},
                  {"partNo":3,"questions":[
                    {"promptText":"Why do places change?"},
                    {"promptText":"How can cities preserve identity?"},
                    {"promptText":"Should governments protect historic places?"}]}
                ]}
                """;
    }

    private record Outcome(boolean accepted, int status) {}
}
