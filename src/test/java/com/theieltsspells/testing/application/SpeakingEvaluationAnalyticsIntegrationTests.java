package com.theieltsspells.testing.application;

import com.theieltsspells.testing.application.dto.SpeakingAiAnalytics.AnswerUpgrade;
import com.theieltsspells.testing.application.dto.SpeakingAiAnalytics.PronunciationIssue;
import com.theieltsspells.testing.application.dto.SpeakingAiAnalytics.SpeechMetrics;
import com.theieltsspells.testing.domain.SpeakingSessionVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "app.speaking-evaluation.enabled=true",
        "app.speaking-evaluation.daily-limit=50",
        "app.writing-evaluation.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
class SpeakingEvaluationAnalyticsIntegrationTests {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("speaking_analytics")
            .withUsername("speaking_test")
            .withPassword("speaking_test");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired SpeakingEvaluationWorker worker;
    @MockBean SpeakingAiEvaluator evaluator;
    @MockBean com.theieltsspells.notification.WritingNotificationService notifications;

    private UUID studentId;
    private UUID attemptId;
    private UUID evaluationId;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
        attemptId = UUID.randomUUID();
        evaluationId = UUID.randomUUID();
        seedAttempt();
        when(evaluator.evaluate(anyList(), any())).thenReturn(result());
    }

    @Test
    void workerPersistsCanonicalAnalyticsAndBothApisReturnTheSameContract() throws Exception {
        worker.processNext();

        assertThat(jdbc.queryForObject(
                "select speech_metrics->>'wordsPerMinute' from public.speaking_evaluations where id=?",
                String.class, evaluationId)).isEqualTo("128");
        assertThat(jdbc.queryForObject(
                "select answer_upgrades->0->>'questionKey' from public.speaking_evaluations where id=?",
                String.class, evaluationId)).isEqualTo("part1_q1");
        assertThat(jdbc.queryForObject(
                "select overall_band from public.speaking_evaluations where id=?",
                BigDecimal.class, evaluationId)).isNull();
        verify(evaluator).evaluate(anyList(), eq(SpeakingSessionVariant.PART_1));

        mvc.perform(get("/api/v1/student/speaking/attempts/{attemptId}/result", attemptId)
                        .with(jwt().jwt(token -> token.subject(studentId.toString()))
                                .authorities(new SimpleGrantedAuthority("student"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variant").value("PART_1"))
                .andExpect(jsonPath("$.partsEvaluated[0]").value(1))
                .andExpect(jsonPath("$.overallBandAvailable").value(false))
                .andExpect(jsonPath("$.aiEligible").value(true))
                .andExpect(jsonPath("$.evaluations[0].variant").value("PART_1"))
                .andExpect(jsonPath("$.evaluations[0].partsEvaluated[0]").value(1))
                .andExpect(jsonPath("$.evaluations[0].overallBandAvailable").value(false))
                .andExpect(jsonPath("$.evaluations[0].overallBand").doesNotExist())
                .andExpect(jsonPath("$.evaluations[0].criteria.fluencyCoherence").value(6.5))
                .andExpect(jsonPath("$.evaluations[0].speechMetrics.wordsPerMinute").value(128))
                .andExpect(jsonPath("$.evaluations[0].speechMetrics.pauseCountEstimated").value(true))
                .andExpect(jsonPath("$.evaluations[0].speechMetrics.fillerWords.um").value(2))
                .andExpect(jsonPath("$.evaluations[0].speechMetrics.pronunciationIssues[0].ipa").value("ˈkʌmftəbl"))
                .andExpect(jsonPath("$.evaluations[0].answerUpgrades[0].questionKey").value("part1_q1"));

        mvc.perform(get("/api/v1/admin/speaking-evaluations/{id}", evaluationId)
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallBand").doesNotExist())
                .andExpect(jsonPath("$.speechMetrics.wordsPerMinute").value(128))
                .andExpect(jsonPath("$.speechMetrics.pauseCountEstimated").value(true))
                .andExpect(jsonPath("$.speechMetrics.pronunciationIssues[0].issue").value("Cần giữ đúng trọng âm"))
                .andExpect(jsonPath("$.answerUpgrades[0].upgradedText").value("I have a keen interest in reading."));
    }

    private SpeakingAiEvaluator.Result result() {
        var criterion = new SpeakingAiEvaluator.Criterion(new BigDecimal("6.5"), "Nhận xét có bằng chứng.");
        var metrics = new SpeechMetrics(128, 3, true, Map.of("um", 2),
                List.of(new PronunciationIssue("comfortable", "ˈkʌmftəbl", "Cần giữ đúng trọng âm")));
        var upgrades = List.of(new AnswerUpgrade("part1_q1", "I like books.",
                "I have a keen interest in reading.", List.of("keen interest in"), "Diễn đạt tự nhiên hơn."));
        return new SpeakingAiEvaluator.Result("GEMINI", "test-model", new BigDecimal("6.5"),
                criterion, criterion, criterion, criterion, metrics, upgrades,
                List.of("Phát âm rõ"), List.of("Giảm ngắt nghỉ"), "{}");
    }

    private void seedAttempt() {
        UUID testId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        String storageKey = "speaking-attempts/" + attemptId + "/" + UUID.randomUUID() + ".webm";
        jdbc.update("insert into auth.users(id) values (?)", studentId);
        jdbc.update("insert into public.profiles(id, full_name, email) values (?, 'Analytics Student', ?)",
                studentId, "student-" + studentId + "@example.test");
        jdbc.update("insert into public.student_profiles(user_id, student_code) values (?, ?)",
                studentId, "SA-" + studentId.toString().substring(0, 8));
        jdbc.update("""
                insert into public.speaking_ai_entitlements(
                  student_id, enabled, unlimited, active_from, source, note)
                values (?, true, true, now(), 'INTEGRATION_TEST', 'Worker to API contract')
                """, studentId);
        jdbc.update("insert into public.tests(id, title, duration_minutes, status, created_by) " +
                "values (?, 'Speaking analytics', 15, cast('DRAFT' as public.publish_status), ?)", testId, studentId);
        jdbc.update("""
                insert into public.test_versions(
                  id, test_id, version_number, version_label, title, duration_minutes, primary_skill,
                  test_type, tags, builder_content, published_by)
                values (?, ?, 1, 'v1', 'Speaking analytics', 15, cast('SPEAKING' as public.skill_type),
                  'SINGLE_SKILL', '[]'::jsonb, '{"parts":[]}'::jsonb, ?)
                """, versionId, testId, studentId);
        jdbc.update("""
                insert into public.test_attempts(
                  id, student_id, attempt_no, status, submitted_at, test_version_id, attempt_origin)
                values (?, ?, 1, cast('SUBMITTED' as public.attempt_status), now(), ?, 'SELF_PRACTICE')
                """, attemptId, studentId, versionId);
        jdbc.update("""
                insert into public.speaking_exam_sessions(
                  attempt_id, student_id, state, state_version, prompt_version, rubric_version,
                  speech_model_version, feature_extractor_version, variant, completed_at)
                values (?, ?, 'EVALUATION_QUEUED', 6, 'test', ?, 'test', 'test', 'PART_1', now())
                """, attemptId, studentId, SpeakingAiEvaluator.RUBRIC_VERSION);
        jdbc.update("""
                insert into public.speaking_audio_assets(
                  attempt_id, student_id, question_key, storage_key, mime_type, size_bytes, duration_ms)
                values (?, ?, 'part1_q1', ?, 'audio/webm', 100, 30000)
                """, attemptId, studentId, storageKey);
        jdbc.update("""
                insert into public.test_attempt_responses(
                  attempt_id, question_key, question_type, answer, normalized_answer, max_score, client_revision)
                values (?, 'part1_q1', 'SPEAKING_QUESTION', cast(? as jsonb), '[]'::jsonb, 9, 1)
                """, attemptId, "{\"storageKey\":\"" + storageKey + "\",\"transcript\":\"Um, I like books.\"}");
        jdbc.update("""
                insert into public.speaking_evaluations(
                  id, attempt_id, student_id, question_key, transcript, status, ai_requested_at,
                  rubric_version, prompt_version)
                values (?, ?, ?, ?, 'part1_q1: Um, I like books.', 'QUEUED', now(), ?, ?)
                """, evaluationId, attemptId, studentId, SpeakingEvaluationService.ATTEMPT_KEY,
                SpeakingAiEvaluator.RUBRIC_VERSION, SpeakingAiEvaluator.PROMPT_VERSION);
    }
}
