package com.theieltsspells.testing.application;

import com.theieltsspells.shared.application.CodedApiException;
import com.theieltsspells.testing.application.dto.SpeakingExamPartTwoReadyRequest;
import com.theieltsspells.testing.application.dto.SubmitSpeakingExaminerTurnRequest;
import com.theieltsspells.testing.domain.SpeakingExamState;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "app.speaking-examiner.enabled=true",
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
    @Autowired JdbcTemplate jdbc;
    @MockBean SpeakingAudioService audioService;
    @MockBean SpeakingExaminerSpeechService speechService;

    private UUID studentId;
    private UUID attemptId;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
        attemptId = UUID.randomUUID();
        when(speechService.modelVersion()).thenReturn("speaches:test");
        when(speechService.promptAudio(anyString())).thenReturn(
                new SpeakingExaminerSpeechService.PromptAudio(CACHE_KEY, "speaking-examiner/tts/test.wav", "audio/wav", 1));
        when(speechService.transcribe(anyString())).thenReturn(
                new SpeachesSpeechClient.Transcription("A recorded answer.", "en", 1_000, List.of(), null));
        when(audioService.durationMsForOwnedStorageKey(any(), any(), anyString(), anyString())).thenReturn(1_000);
        seedAttempt();
    }

    @Test
    void resumesThePersistedPartTwoRouteAfterRefresh() {
        var session = sessions.createOrResume(attemptId, studentId);
        for (int index = 0; index < 5; index++) session = submitCurrent(session);

        assertThat(session.state()).isEqualTo(SpeakingExamState.PART_2_PREPARATION);
        var refreshed = sessions.get(session.sessionId(), studentId);
        assertThat(refreshed.stateVersion()).isEqualTo(session.stateVersion());
        assertThat(refreshed.currentTurn().turnId()).isEqualTo(session.currentTurn().turnId());
        assertThat(refreshed.currentTurn().topicKey()).isEqualTo("memorable-place");

        var ready = sessions.markPartTwoReady(session.sessionId(),
                new SpeakingExamPartTwoReadyRequest(session.stateVersion()), studentId);
        assertThat(ready.state()).isEqualTo(SpeakingExamState.PART_2_LONG_TURN);
        assertThat(sessions.get(session.sessionId(), studentId).currentTurn().turnId())
                .isEqualTo(ready.currentTurn().turnId());
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
    void replaysTheSameClientTurnWithTheOriginalNextTurnAfterTheSessionAdvances() {
        var session = sessions.createOrResume(attemptId, studentId);
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
        var session = sessions.createOrResume(attemptId, studentId);
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
                    {"topicKey":"future-home","promptText":"Would you move one day?"}]},
                  {"id":"memorable-place","partNo":2,"cueCardPromptHtml":"Describe a memorable place.",
                    "followUpQuestions":[{"promptText":"Why was it memorable?"}]},
                  {"partNo":3,"questions":[{"promptText":"Why do places change?"}]}
                ]}
                """;
    }

    private record Outcome(boolean accepted, int status) {}
}
