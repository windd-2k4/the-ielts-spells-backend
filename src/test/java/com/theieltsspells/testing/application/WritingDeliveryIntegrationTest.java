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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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
        task.put("imageAltText", "Line chart used for the Writing Task 1 prompt");
        task.put("minWords", 150);
        task.put("suggestedTimeMinutes", 20);
        task.put("responseMode", "FREEFORM");

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

        var attempt = writingDeliveryService.startOrResumeSelfPractice(versionId, studentId);
        assertEquals("IN_PROGRESS", attempt.status());
        assertEquals("task-1", attempt.tasks().getFirst().taskKey());

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
        assertTrue(submitted.evaluations().isEmpty());

        UUID evaluationId = jdbc.queryForObject("""
                select evaluation.id from public.writing_evaluations evaluation
                join public.test_attempt_responses response on response.id=evaluation.test_attempt_response_id
                where response.attempt_id=?
                """, UUID.class, attempt.attemptId());
        assertNotNull(evaluationId);

        Map<String, ReviewWritingEvaluationRequest.CriterionReview> criteria = Map.of(
                "task_achievement", criterion("6.5"),
                "coherence_cohesion", criterion("6.0"),
                "lexical_resource", criterion("6.5"),
                "grammatical_range_accuracy", criterion("6.0")
        );
        evaluationService.review(evaluationId, new ReviewWritingEvaluationRequest(
                new BigDecimal("6.5"), criteria, List.of("Clear overview"),
                List.of("Develop comparisons"), true), reviewerId);

        var result = writingDeliveryService.getResult(attempt.attemptId(), studentId);
        assertEquals(1, result.evaluations().size());
        assertEquals(new BigDecimal("6.5"), result.evaluations().getFirst().overallBand());
        assertEquals(4, result.evaluations().getFirst().criterionBands().size());
        assertEquals(List.of("Clear overview"), result.evaluations().getFirst().strengths());
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
