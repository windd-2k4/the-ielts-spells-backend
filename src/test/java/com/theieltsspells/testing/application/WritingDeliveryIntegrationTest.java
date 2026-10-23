package com.theieltsspells.testing.application;

import com.theieltsspells.academic.application.AcademicMembershipService;
import com.theieltsspells.shared.persistence.enums.SkillType;
import com.theieltsspells.testing.application.dto.SaveWritingResponseItem;
import com.theieltsspells.testing.application.dto.SaveWritingResponsesRequest;
import com.theieltsspells.testing.application.dto.TestBankRequest;
import com.theieltsspells.writingevaluation.application.WritingEvaluationApplicationService;
import com.theieltsspells.writingevaluation.application.dto.ReviewWritingEvaluationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.task.scheduling.enabled=false", "app.writing-evaluation.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Transactional
class WritingDeliveryIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("writing_delivery")
            .withUsername("writing_test")
            .withPassword("writing_test");

    private static final String EXTERNAL_JDBC_URL = System.getProperty("writing.test.jdbc-url");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (EXTERNAL_JDBC_URL == null || EXTERNAL_JDBC_URL.isBlank()) {
            POSTGRES.start();
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
            registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_JDBC_URL);
            registry.add("spring.datasource.username", () -> System.getProperty("writing.test.username", "writing_test"));
            registry.add("spring.datasource.password", () -> System.getProperty("writing.test.password", "writing_test"));
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        }
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired TestBankApplicationService testBankService;
    @Autowired StudentWritingDeliveryService writingDeliveryService;
    @Autowired WritingEvaluationApplicationService evaluationService;
    @Autowired JdbcTemplate jdbc;
    @MockBean AcademicMembershipService membershipService;
    @MockBean com.theieltsspells.learninglibrary.infrastructure.storage.FileStorageService fileStorageService;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;

    @Test
    void httpReviewPublishesOnlyToTheOwnerAndRejectsMissingCriterion() throws Exception {
        UUID id = submittedTaskTwo();
        var evaluation = evaluationService.get(id);
        UUID reviewer = profile("http-reviewer");
        var teacher = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.subject(reviewer.toString()))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("teacher"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/writing-evaluations/" + id).with(teacher))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.taskContext.taskKey").value("task-2"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/writing-evaluations/" + id + "/review")
                .with(teacher).contentType("application/json").content("{\"overallBand\":7,\"criteria\":{\"task_response\":null},\"publish\":true}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/writing-evaluations/" + id + "/review")
                .with(teacher).contentType("application/json").content(objectMapper.writeValueAsString(review(true))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        String url = "/api/v1/student/writing/attempts/" + evaluation.attemptId() + "/result";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.subject(evaluation.studentId().toString()))
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("student"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.evaluations[0].overallBandTeacher").value(7));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.subject(UUID.randomUUID().toString()))
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("student"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void workerCannotOverwriteTeacherReviewThatFinishesDuringModelCall() throws Exception {
        UUID evaluationId = submittedTaskTwo();
        UUID reviewerId = profile("reviewer-during-ai");
        var evaluator = org.mockito.Mockito.mock(com.theieltsspells.writingevaluation.infrastructure.WritingAiEvaluator.class);
        org.mockito.Mockito.when(evaluator.evaluate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    evaluationService.review(evaluationId, review(true), reviewerId);
                    return aiResult();
                });
        worker(evaluator).processNext();
        assertEquals("AI_COMPLETED", evaluationService.get(evaluationId).status());
        assertEquals("PUBLISHED", evaluationService.get(evaluationId).teacherStatus());
        assertEquals(List.of("Teacher strength"), evaluationService.get(evaluationId).strengths());
        assertEquals(new BigDecimal("6.5"), evaluationService.get(evaluationId).overallBandAi());
        assertEquals(4, evaluationService.get(evaluationId).criteria().size());
    }

    @Test
    void aiResultIsReleasedAutomaticallyWhileTeacherResultWaitsForPublication() throws Exception {
        UUID evaluationId = submittedTaskTwo();
        var evaluator = org.mockito.Mockito.mock(com.theieltsspells.writingevaluation.infrastructure.WritingAiEvaluator.class);
        org.mockito.Mockito.when(evaluator.evaluate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(aiResult());
        worker(evaluator).processNext();
        var evaluation = evaluationService.get(evaluationId);
        assertEquals("AI_COMPLETED", evaluation.status());
        assertEquals(4, evaluation.criteria().size());
        assertEquals(1, writingDeliveryService.getResult(evaluation.attemptId(), evaluation.studentId()).evaluations().size());
        evaluationService.review(evaluationId, review(false), profile("draft-reviewer"));
        var draftResult = writingDeliveryService.getResult(evaluation.attemptId(), evaluation.studentId()).evaluations().getFirst();
        assertEquals(new BigDecimal("6.5"), draftResult.overallBandAi());
        assertEquals(null, draftResult.overallBandTeacher());
        evaluationService.review(evaluationId, review(true), profile("publish-reviewer"));
        assertEquals(1, writingDeliveryService.getResult(evaluation.attemptId(), evaluation.studentId()).evaluations().size());
        assertEquals("[\"AI strength\"]", jdbc.queryForObject("select ai_strengths::text from public.writing_evaluations where id=?", String.class, evaluationId));
        assertEquals(2, jdbc.queryForObject("select count(*) from public.writing_evaluation_reviews where evaluation_id=?", Integer.class, evaluationId));
    }

    @Test
    void failedAndInterruptedJobsAreRecoverableAndProviderDetailsStayPrivate() throws Exception {
        UUID id = submittedTaskTwo();
        var evaluator = org.mockito.Mockito.mock(com.theieltsspells.writingevaluation.infrastructure.WritingAiEvaluator.class);
        org.mockito.Mockito.when(evaluator.evaluate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("provider-secret-must-not-leak"));
        var worker = worker(evaluator);
        worker.processNext();
        assertEquals("FAILED", evaluationService.get(id).status());
        assertFalse(evaluationService.get(id).errorMessage().contains("provider-secret"));
        assertEquals("QUEUED", evaluationService.retry(id).status());
        jdbc.update("update public.writing_evaluations set status='PROCESSING', processing_token=?, processing_started_at=now()-interval '2 hours' where id=?",
                UUID.randomUUID(), id);
        worker.processNext();
        assertEquals("FAILED", evaluationService.get(id).status());
        assertEquals("QUEUED", evaluationService.retry(id).status());
    }

    @Test
    void writingQueueFiltersAndPaginatesOnTheServer() {
        var before = evaluationService.filterOptions();
        UUID id = submittedTaskTwo();

        var page = evaluationService.list(null, "SELF_PRACTICE", null, "AVAILABLE",
                "task-two-student", 0, 10);

        assertEquals(1, page.totalElements());
        assertEquals(id, page.content().getFirst().id());
        assertTrue(page.content().getFirst().studentCode().startsWith("WR-"));
        assertTrue(page.first());
        assertTrue(page.last());
        var options = evaluationService.filterOptions();
        assertEquals(before.selfPracticeCount() + 1, options.selfPracticeCount());
        assertEquals(before.courseCount(), options.courseCount());
    }

    private com.theieltsspells.writingevaluation.application.WritingEvaluationWorker worker(
            com.theieltsspells.writingevaluation.infrastructure.WritingAiEvaluator evaluator) {
        return new com.theieltsspells.writingevaluation.application.WritingEvaluationWorker(jdbc, objectMapper, evaluator, transactionManager,
                new com.theieltsspells.notification.WritingNotificationService(jdbc));
    }

    private com.theieltsspells.writingevaluation.infrastructure.WritingAiEvaluator.Result aiResult() {
        var criteria = new LinkedHashMap<String, com.theieltsspells.writingevaluation.infrastructure.WritingAiEvaluator.Criterion>();
        for (String key : List.of("task_response", "coherence_cohesion", "lexical_resource", "grammatical_range_accuracy")) {
            criteria.put(key, new com.theieltsspells.writingevaluation.infrastructure.WritingAiEvaluator.Criterion(new BigDecimal("6.5"), "AI feedback", List.of("Stored essay.")));
        }
        return new com.theieltsspells.writingevaluation.infrastructure.WritingAiEvaluator.Result("TEST", "test-model", new BigDecimal("6.5"),
                criteria, List.of("AI strength"), List.of("AI improvement"), List.of(), null, objectMapper.createObjectNode());
    }

    private ReviewWritingEvaluationRequest review(boolean publish) {
        return new ReviewWritingEvaluationRequest(new BigDecimal("7.0"), Map.of(
                "task_response", criterion("7.0"), "coherence_cohesion", criterion("7.0"),
                "lexical_resource", criterion("7.0"), "grammatical_range_accuracy", criterion("7.0")),
                List.of("Teacher strength"), List.of("Teacher improvement"), publish);
    }

    private UUID submittedTaskTwo() {
        UUID author = profile("task-two-author");
        UUID student = profile("task-two-student");
        jdbc.update("insert into public.student_profiles(user_id, student_code) values (?, ?)", student, "WR-" + student.toString().substring(0, 8));
        var created = testBankService.create(new TestBankRequest("Task two", "Task two test", SkillType.WRITING,
                "SINGLE_SKILL", 40, "v1.0-draft", List.of("WRITING"),
                Map.of("format", "ACADEMIC", "tasks", List.of(Map.of("id", "task-2", "taskNo", 2,
                        "promptHtml", "<p>Should cities provide free public transport? Discuss.</p>", "minWords", 250,
                        "suggestedTimeMinutes", 40, "responseMode", "FREEFORM"))), null), author);
        var published = testBankService.changeStatus(created.id(), "PUBLISHED", created.draftRevision(), author, true);
        var attempt = writingDeliveryService.startOrResumeSelfPractice(published.publishedVersion().id(), student);
        writingDeliveryService.saveResponses(attempt.attemptId(), new SaveWritingResponsesRequest(List.of(new SaveWritingResponseItem("task-2", "Stored essay.", 1))), student);
        writingDeliveryService.submit(attempt.attemptId(), student);
        jdbc.update("insert into public.writing_ai_entitlements(student_id, enabled, unlimited, active_from) values (?, true, true, now())", student);
        evaluationService.requestAi(attempt.attemptId(), student);
        return jdbc.queryForObject("select e.id from public.writing_evaluations e join public.test_attempt_responses r on r.id=e.test_attempt_response_id where r.attempt_id=?", UUID.class, attempt.attemptId());
    }

    @Test
    void publishedWritingCanBeSavedSubmittedReviewedAndReleased() {
        UUID authorId = profile("writing-author");
        UUID studentId = profile("writing-student");
        UUID reviewerId = profile("writing-reviewer");
        jdbc.update("insert into public.student_profiles(user_id, student_code) values (?, ?)",
                studentId, "WR-" + studentId.toString().substring(0, 8));

        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", "task-1");
        task.put("taskNo", 1);
        task.put("title", "Academic Writing Task 1");
        task.put("promptHtml", "<p>Summarise the information shown in the chart.</p><script>alert('x')</script>");
        task.put("imageUrl", "https://example.test/writing-task-1-chart.png");
        task.put("imageAssetId", UUID.randomUUID().toString());
        task.put("imageAltText", "Line chart used for the Writing Task 1 prompt");
        task.put("minWords", 150);
        task.put("suggestedTimeMinutes", 20);
        task.put("responseMode", "FREEFORM");
        task.put("sampleBand8Answer", "<p>A reference answer.</p><script>alert('x')</script>");

        var created = testBankService.create(new TestBankRequest(
                "Writing delivery integration", "Immutable Writing delivery", SkillType.WRITING,
                "SINGLE_SKILL", 20, "v1.0-draft", List.of("WRITING", "ACADEMIC"),
                Map.of("format", "ACADEMIC", "tasks", List.of(task)), null), authorId);
        var published = testBankService.changeStatus(created.id(), "PUBLISHED", created.draftRevision(), authorId, true);
        UUID versionId = published.publishedVersion().id();

        String publishedPrompt = jdbc.queryForObject(
                "select builder_content->'tasks'->0->>'promptHtml' from public.test_versions where id=?",
                String.class, versionId);
        assertFalse(publishedPrompt.contains("<script"));

        var firstAttempt = writingDeliveryService.startOrResumeSelfPractice(versionId, studentId);
        var resumedAttempt = writingDeliveryService.startOrResumeSelfPractice(versionId, studentId);
        assertEquals(firstAttempt.attemptId(), resumedAttempt.attemptId());

        var attempt = writingDeliveryService.startOrResumeSelfPractice(versionId, studentId, true);
        assertNotEquals(firstAttempt.attemptId(), attempt.attemptId());
        assertEquals("EXPIRED", jdbc.queryForObject(
                "select status::text from public.test_attempts where id = ?", String.class, firstAttempt.attemptId()));
        assertEquals("IN_PROGRESS", attempt.status());
        assertEquals("task-1", attempt.tasks().getFirst().taskKey());
        assertEquals(null, attempt.tasks().getFirst().sampleBand8Answer());

        var saved = writingDeliveryService.saveResponses(attempt.attemptId(), new SaveWritingResponsesRequest(List.of(
                new SaveWritingResponseItem("task-1", "A newer answer with enough detail for review.", 2)
        )), studentId);
        assertEquals(2, saved.responses().getFirst().clientRevision());

        var staleSave = writingDeliveryService.saveResponses(attempt.attemptId(), new SaveWritingResponsesRequest(List.of(
                new SaveWritingResponseItem("task-1", "This stale edit must not replace revision two.", 1)
        )), studentId);
        assertEquals("A newer answer with enough detail for review.", staleSave.responses().getFirst().text());

        var submitted = writingDeliveryService.submit(attempt.attemptId(), studentId);
        assertEquals("SUBMITTED", submitted.status());
        assertTrue(submitted.resultVisible());
        assertEquals("NOT_ELIGIBLE", submitted.evaluations().getFirst().aiStatus());
        assertFalse(submitted.aiEligible());
        assertTrue(submitted.tasks().getFirst().sampleBand8Answer().contains("reference answer"));
        assertFalse(submitted.tasks().getFirst().sampleBand8Answer().contains("<script"));
        org.junit.jupiter.api.Assertions.assertThrows(com.theieltsspells.shared.application.BusinessRuleException.class,
                () -> evaluationService.requestAi(attempt.attemptId(), studentId));
        jdbc.update("insert into public.writing_ai_entitlements(student_id, enabled, unlimited, monthly_limit, active_from) values (?, true, false, 1, now())", studentId);
        assertTrue(writingDeliveryService.getResult(attempt.attemptId(), studentId).aiEligible());
        // Submission remains passive even for entitled students.
        assertEquals("NOT_ELIGIBLE", writingDeliveryService.submit(attempt.attemptId(), studentId).evaluations().getFirst().aiStatus());
        org.junit.jupiter.api.Assertions.assertThrows(com.theieltsspells.shared.application.ResourceNotFoundException.class,
                () -> evaluationService.requestAi(attempt.attemptId(), UUID.randomUUID()));
        assertEquals(1, evaluationService.requestAi(attempt.attemptId(), studentId));
        assertEquals(0, evaluationService.requestAi(attempt.attemptId(), studentId));
        assertFalse(writingDeliveryService.getResult(attempt.attemptId(), studentId).aiEligible());

        UUID evaluationId = jdbc.queryForObject("""
                select evaluation.id from public.writing_evaluations evaluation
                join public.test_attempt_responses response on response.id=evaluation.test_attempt_response_id
                where response.attempt_id=?
                """, UUID.class, attempt.attemptId());
        assertNotNull(evaluationId);
        assertTrue(evaluationService.get(evaluationId).taskContext().get("promptHtml").toString().contains("Summarise"));
        writingDeliveryService.submit(attempt.attemptId(), studentId);
        assertEquals(1, jdbc.queryForObject("select count(*) from public.writing_evaluations where test_attempt_response_id in (select id from public.test_attempt_responses where attempt_id=?)",
                Integer.class, attempt.attemptId()));

        Map<String, ReviewWritingEvaluationRequest.CriterionReview> criteria = Map.of(
                "task_achievement", criterion("6.5"),
                "coherence_cohesion", criterion("6.0"),
                "lexical_resource", criterion("6.5"),
                "grammatical_range_accuracy", criterion("6.0")
        );
        evaluationService.review(evaluationId, new ReviewWritingEvaluationRequest(
                new BigDecimal("6.5"), criteria, List.of("Clear overview"),
                List.of("Develop comparisons"), true,
                "Edited teacher version with a clearer comparison.",
                "<p><strong>Edited teacher version</strong> with a clearer comparison.</p><script>alert(1)</script>",
                List.of(new com.theieltsspells.writingevaluation.application.dto.WritingTeacherAnnotation(
                        "annotation-1", 0, 12, "A newer answer", "The response", "Use a precise opening", "task_achievement", "REPLACE", "SUGGESTION"))), reviewerId);

        var result = writingDeliveryService.getResult(attempt.attemptId(), studentId);
        assertEquals(1, result.evaluations().size());
        assertEquals("QUEUED", result.evaluations().getFirst().aiStatus());
        assertEquals("PUBLISHED", result.evaluations().getFirst().teacherStatus());
        assertEquals(new BigDecimal("6.5"), result.evaluations().getFirst().overallBandTeacher());
        assertEquals(4, result.evaluations().getFirst().teacherCriterionBands().size());
        assertEquals(List.of("Clear overview"), result.evaluations().getFirst().teacherStrengths());
        assertEquals("Edited teacher version with a clearer comparison.", result.evaluations().getFirst().teacherEditedText());
        assertTrue(result.evaluations().getFirst().teacherEditedHtml().contains("<strong>Edited teacher version</strong>"));
        assertFalse(result.evaluations().getFirst().teacherEditedHtml().contains("<script"));
        assertEquals(1, result.evaluations().getFirst().teacherAnnotations().size());
        var notifications = new com.theieltsspells.notification.WritingNotificationService(jdbc);
        var notice = notifications.unread(studentId).getFirst();
        notifications.markRead(notice.id(), UUID.randomUUID());
        assertEquals(1, notifications.unread(studentId).size());
        notifications.markRead(notice.id(), studentId);
        assertTrue(notifications.unread(studentId).isEmpty());
        assertEquals(1, jdbc.queryForObject("select count(*) from public.writing_evaluation_reviews where evaluation_id=?", Integer.class, evaluationId));
        org.junit.jupiter.api.Assertions.assertThrows(com.theieltsspells.shared.application.ConflictException.class,
                () -> evaluationService.retry(evaluationId));
    }

    private ReviewWritingEvaluationRequest.CriterionReview criterion(String band) {
        return new ReviewWritingEvaluationRequest.CriterionReview(new BigDecimal(band), "Teacher feedback");
    }

    private UUID profile(String prefix) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into public.profiles(id, email, full_name) values (?, ?, ?)",
                id, prefix + "+" + id + "@example.test", prefix);
        return id;
    }
}
