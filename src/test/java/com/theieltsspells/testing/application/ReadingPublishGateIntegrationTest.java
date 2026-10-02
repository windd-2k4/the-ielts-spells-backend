package com.theieltsspells.testing.application;

import com.theieltsspells.academic.application.AcademicMembershipService;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.persistence.enums.SkillType;
import com.theieltsspells.testing.application.dto.CreateTestAssignmentRequest;
import com.theieltsspells.testing.application.dto.TestBankRequest;
import com.theieltsspells.testing.application.dto.TestBankResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
class ReadingPublishGateIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("reading_publish_gate")
            .withUsername("reading_test")
            .withPassword("reading_test");

    private static final String EXTERNAL_JDBC_URL = System.getProperty("reading.test.jdbc-url");

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
            registry.add("spring.datasource.username", () -> System.getProperty("reading.test.username", "reading_test"));
            registry.add("spring.datasource.password", () -> System.getProperty("reading.test.password", "reading_test"));
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        }
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @AfterAll
    static void stopManagedPostgres() {
        if ((EXTERNAL_JDBC_URL == null || EXTERNAL_JDBC_URL.isBlank()) && POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @Autowired
    private TestBankApplicationService testBankService;

    @Autowired
    private StudentReadingDeliveryService studentDeliveryService;

    @Autowired
    private TestAssignmentApplicationService assignmentService;

    @SpyBean
    private ReadingVersionMaterializer readingVersionMaterializer;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AcademicMembershipService membershipService;

    private UUID actorId;
    private UUID nonModeratorId;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        nonModeratorId = UUID.randomUUID();
        jdbc.update("insert into public.profiles (id, email, full_name) values (?, ?, ?) on conflict do nothing",
                actorId, "admin@example.test", "Admin User");
        jdbc.update("insert into public.profiles (id, email, full_name) values (?, ?, ?) on conflict do nothing",
                nonModeratorId, "teacher@example.test", "Teacher User");
        when(membershipService.hasActiveEnrollment(any(), any())).thenReturn(true);
        when(membershipService.courseExists(any())).thenReturn(true);
    }

    @AfterEach
    void resetMaterializerSpy() {
        reset(readingVersionMaterializer);
    }

    @Test
    void testPublishSuccess_FullTest() {
        var builderContent = createValidReadingBuilderContent(3, 40);
        var request = new TestBankRequest(
                "Full IELTS Reading Practice 1",
                "Full Reading test description",
                SkillType.READING,
                "FULL_TEST",
                60,
                "v1.0-draft",
                List.of("READING", "ACADEMIC"),
                builderContent,
                null
        );

        TestBankResponse created = testBankService.create(request, actorId);
        assertNotNull(created.id());
        assertEquals("DRAFT", created.status());

        TestBankResponse published = testBankService.changeStatus(created.id(), "PUBLISHED", 1, actorId, true);

        assertEquals("PUBLISHED", published.status());
        assertNotNull(published.publishedVersion());
        assertEquals(2, published.draftRevision());

        Integer versionCount = jdbc.queryForObject(
                "select count(*) from public.test_versions where test_id = ?", Integer.class, created.id());
        assertEquals(1, versionCount);

        UUID versionId = published.publishedVersion().id();

        Integer sectionCount = jdbc.queryForObject(
                "select count(*) from public.test_version_sections where test_version_id = ?", Integer.class, versionId);
        assertEquals(3, sectionCount);

        Integer questionCount = jdbc.queryForObject(
                "select count(*) from public.test_version_questions where test_version_id = ?", Integer.class, versionId);
        assertEquals(40, questionCount);

        Integer optionCount = jdbc.queryForObject(
                "select count(*) from public.test_version_question_options where test_version_id = ?", Integer.class, versionId);
        assertTrue(optionCount > 0);
    }

    @Test
    void testPublishSuccess_SingleSkill() {
        var builderContent = createValidReadingBuilderContent(1, 13);
        var request = new TestBankRequest(
                "Single Skill Passage 1",
                "Single passage reading test",
                SkillType.READING,
                "SINGLE_SKILL",
                20,
                "v1.0-draft",
                List.of("READING"),
                builderContent,
                null
        );

        TestBankResponse created = testBankService.create(request, actorId);
        TestBankResponse published = testBankService.changeStatus(created.id(), "PUBLISHED", 1, actorId, true);

        assertEquals("PUBLISHED", published.status());
        UUID versionId = published.publishedVersion().id();

        Integer sectionCount = jdbc.queryForObject(
                "select count(*) from public.test_version_sections where test_version_id = ?", Integer.class, versionId);
        assertEquals(1, sectionCount);

        Integer questionCount = jdbc.queryForObject(
                "select count(*) from public.test_version_questions where test_version_id = ?", Integer.class, versionId);
        assertEquals(13, questionCount);
    }

    @Test
    void materializesStructuredSolutionsAndMultipleEvidenceForStudentDelivery() {
        UUID directEvidenceId = UUID.randomUUID();
        UUID noDirectEvidenceId = UUID.randomUUID();
        var builderContent = createValidReadingBuilderContent(1, 5);
        @SuppressWarnings("unchecked")
        var questions = (List<Map<String, Object>>) ((List<Map<String, Object>>) passages(builderContent)
                .getFirst().get("questionGroups")).getFirst().get("questions");
        var firstQuestion = questions.getFirst();
        firstQuestion.put("explanation", "<p>The passage explicitly states this.</p>");
        firstQuestion.put("reasoningSteps", List.of(
                "<p>Locate the matching statement.</p>",
                "<p>Compare the meaning with option A.</p>"
        ));
        firstQuestion.put("trapAnalysis", "<p>Option B reverses the meaning.</p>");
        firstQuestion.put("vocabularyNotes", "<p><strong>flexibility</strong> means adaptability.</p>");
        firstQuestion.put("relatedLessonUrl", "https://example.test/lessons/reading-flexibility");
        firstQuestion.put("evidenceSpans", List.of(
                Map.of(
                        "id", directEvidenceId.toString(),
                        "start", 0,
                        "end", 4,
                        "quote", "This",
                        "prefix", "",
                        "suffix", " is authentic",
                        "paragraphKey", "paragraph-1",
                        "label", "Bằng chứng chính",
                        "mode", "DIRECT_QUOTE"
                ),
                Map.of(
                        "id", noDirectEvidenceId.toString(),
                        "label", "Không có thông tin trực tiếp",
                        "mode", "NO_DIRECT_EVIDENCE"
                )
        ));
        var teacherOnlyQuestion = questions.get(1);
        teacherOnlyQuestion.put("explanation", "<p>Teacher-only rationale.</p>");
        teacherOnlyQuestion.put("teacherNote", "Private authoring note");
        teacherOnlyQuestion.put("solutionVisibility", "TEACHER_ONLY");
        teacherOnlyQuestion.put("evidenceSpans", List.of(Map.of(
                "start", 0,
                "end", 4,
                "quote", "This",
                "mode", "DIRECT_QUOTE"
        )));

        var created = createSingleReading("Structured solution test", builderContent);
        var published = testBankService.changeStatus(
                created.id(), "PUBLISHED", created.draftRevision(), actorId, true);
        UUID versionId = published.publishedVersion().id();

        assertEquals(2, jdbc.queryForObject("""
                select count(*) from public.test_version_question_evidence evidence
                join public.test_version_questions question on question.id = evidence.question_id
                where evidence.test_version_id = ? and question.question_key = 'q-id-1'
                """, Integer.class, versionId));
        assertTrue(jdbc.queryForObject("""
                select reasoning_steps::text from public.test_version_questions
                where test_version_id = ? and question_key = 'q-id-1'
                """, String.class, versionId).contains("Locate the matching statement"));

        UUID studentId = UUID.randomUUID();
        jdbc.update("insert into public.profiles(id, email, full_name) values (?, ?, ?)",
                studentId, "solution-" + studentId + "@example.test", "Solution Student");
        jdbc.update("insert into public.student_profiles(user_id, student_code) values (?, ?)",
                studentId, "SS-" + studentId.toString().substring(0, 8));
        var attempt = studentDeliveryService.startOrResumeSelfPractice(versionId, studentId);
        var result = studentDeliveryService.submit(attempt.attemptId(), studentId);
        var question = result.questions().stream()
                .filter(item -> item.questionKey().equals("q-id-1"))
                .findFirst().orElseThrow();

        assertEquals("<p>The passage explicitly states this.</p>", question.explanation());
        assertNotNull(question.solution());
        assertEquals(List.of("<p>Locate the matching statement.</p>", "<p>Compare the meaning with option A.</p>"),
                question.solution().reasoningSteps());
        assertEquals("<p>Option B reverses the meaning.</p>", question.solution().trapAnalysis());
        assertEquals("<p><strong>flexibility</strong> means adaptability.</p>", question.solution().vocabularyNotes());
        assertEquals("https://example.test/lessons/reading-flexibility", question.solution().relatedLessonUrl());
        assertEquals(2, question.evidenceSpans().size());
        assertEquals(directEvidenceId, question.evidenceSpans().getFirst().id());
        assertEquals("DIRECT_QUOTE", question.evidenceSpans().getFirst().mode().name());
        assertEquals("Bằng chứng chính", question.evidenceSpan().label());
        assertEquals("DIRECT_QUOTE", question.evidenceSpan().mode().name());
        assertEquals(noDirectEvidenceId, question.evidenceSpans().get(1).id());
        assertNull(question.evidenceSpans().get(1).start());
        assertNull(question.evidenceSpans().get(1).end());

        var teacherOnly = result.questions().stream()
                .filter(item -> item.questionKey().equals("q-id-2"))
                .findFirst().orElseThrow();
        assertNull(teacherOnly.explanation());
        assertNull(teacherOnly.solution());
        assertTrue(teacherOnly.evidenceSpans().isEmpty());
        assertNull(teacherOnly.evidenceSpan());
    }

    @Test
    void testPublishFailure_ValidationErrors() {
        // FULL_TEST with only 2 passages (should fail validation)
        var invalidContent = createValidReadingBuilderContent(2, 20);
        var request = new TestBankRequest(
                "Invalid Full Reading Test",
                "Description",
                SkillType.READING,
                "FULL_TEST",
                60,
                "v1.0-draft",
                List.of("READING"),
                invalidContent,
                null
        );

        TestBankResponse created = testBankService.create(request, actorId);

        assertThrows(BusinessRuleException.class, () ->
                testBankService.changeStatus(created.id(), "PUBLISHED", 1, actorId, true)
        );

        // Verify status remains DRAFT and no versions created
        TestBankResponse after = testBankService.get(created.id());
        assertEquals("DRAFT", after.status());
        assertNull(after.publishedVersion());

        Integer versionCount = jdbc.queryForObject(
                "select count(*) from public.test_versions where test_id = ?", Integer.class, created.id());
        assertEquals(0, versionCount);
    }

    @Test
    void testPublishFailure_StaleDraftConflict() {
        var builderContent = createValidReadingBuilderContent(1, 5);
        var request = new TestBankRequest(
                "Concurrency Test",
                "Description",
                SkillType.READING,
                "SINGLE_SKILL",
                15,
                "v1.0-draft",
                List.of("READING"),
                builderContent,
                null
        );

        TestBankResponse created = testBankService.create(request, actorId);

        // Try publishing with wrong revision (e.g. 99 instead of 1)
        assertThrows(ConflictException.class, () ->
                testBankService.changeStatus(created.id(), "PUBLISHED", 99, actorId, true)
        );
    }

    @Test
    void testPublishFailure_UnauthorizedUser() {
        var builderContent = createValidReadingBuilderContent(1, 5);
        var request = new TestBankRequest(
                "Security Test",
                "Description",
                SkillType.READING,
                "SINGLE_SKILL",
                15,
                "v1.0-draft",
                List.of("READING"),
                builderContent,
                null
        );

        TestBankResponse created = testBankService.create(request, actorId);

        // Non-moderator tries to publish
        assertThrows(BusinessRuleException.class, () ->
                testBankService.changeStatus(created.id(), "PUBLISHED", 1, nonModeratorId, false)
        );
    }

    @Test
    void publishEndpointAllowsAdminAndForbidsTeacher() throws Exception {
        var created = createSingleReading("HTTP Security Test", createValidReadingBuilderContent(1, 5));
        String requestBody = "{\"status\":\"PUBLISHED\",\"draftRevision\":" + created.draftRevision() + "}";

        mockMvc.perform(patch("/api/v1/admin/test-bank/{id}/status", created.id())
                        .with(jwt().jwt(token -> token.subject(nonModeratorId.toString()))
                                .authorities(new SimpleGrantedAuthority("teacher")))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/admin/test-bank/{id}/status", created.id())
                        .with(jwt().jwt(token -> token.subject(actorId.toString()))
                                .authorities(new SimpleGrantedAuthority("admin")))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void testVersionImmutability() {
        var builderContent = createValidReadingBuilderContent(1, 5);
        var request = new TestBankRequest(
                "Immutability Test Original",
                "Original Description",
                SkillType.READING,
                "SINGLE_SKILL",
                15,
                "v1.0-draft",
                List.of("READING"),
                builderContent,
                null
        );

        TestBankResponse created = testBankService.create(request, actorId);
        TestBankResponse published = testBankService.changeStatus(created.id(), "PUBLISHED", 1, actorId, true);
        UUID versionId = published.publishedVersion().id();

        // Mutate draft in tests table
        jdbc.update("update public.tests set title = 'MUTATED TITLE' where id = ?", created.id());

        // Verify version title remains unchanged
        String versionTitle = jdbc.queryForObject(
                "select title from public.test_versions where id = ?", String.class, versionId);
        assertEquals("Immutability Test Original", versionTitle);

        assertThrows(DataAccessException.class, () ->
                jdbc.update("update public.test_versions set title = 'ILLEGAL MUTATION' where id = ?", versionId));
    }

    @Test
    void testHtmlSanitization() {
        var passage = new LinkedHashMap<String, Object>();
        passage.put("id", "passage-xss-1");
        passage.put("passageNo", 1);
        passage.put("title", "Reading Passage XSS");
        passage.put("content", "<div><script>alert('xss')</script><p>Clean passage text</p></div>");

        var group = new LinkedHashMap<String, Object>();
        group.put("id", "group-xss-1");
        group.put("title", "Questions 1–1");
        group.put("typeFormat", "MULTIPLE_CHOICE");
        group.put("instructions", "<p>Instructions <iframe src='http://evil.com'></iframe></p>");

        var question = new LinkedHashMap<String, Object>();
        question.put("id", "q-xss-1");
        question.put("number", 1);
        question.put("typeFormat", "MULTIPLE_CHOICE");
        question.put("prompt", "<p>Question prompt <img src='x' onerror='alert(1)'></p>");
        question.put("correctAnswers", List.of("opt-a"));
        question.put("options", List.of(
                Map.of("id", "opt-a", "code", "A", "text", "Option <b>A</b>"),
                Map.of("id", "opt-b", "code", "B", "text", "Option B")
        ));
        group.put("questions", List.of(question));
        passage.put("questionGroups", List.of(group));

        var builderContent = Map.<String, Object>of("passages", List.of(passage));
        var request = new TestBankRequest(
                "XSS Test Reading",
                "Description",
                SkillType.READING,
                "SINGLE_SKILL",
                15,
                "v1.0-draft",
                List.of("READING"),
                builderContent,
                null
        );

        TestBankResponse created = testBankService.create(request, actorId);
        TestBankResponse published = testBankService.changeStatus(created.id(), "PUBLISHED", 1, actorId, true);
        UUID versionId = published.publishedVersion().id();

        String sanitizedSection = jdbc.queryForObject(
                "select content_html from public.test_version_sections where test_version_id = ?", String.class, versionId);
        assertTrue(sanitizedSection.contains("Clean passage text"));
        assertFalse(sanitizedSection.contains("<script>"));

        String sanitizedInstructions = jdbc.queryForObject(
                "select instructions from public.test_version_question_groups where test_version_id = ?", String.class, versionId);
        assertTrue(sanitizedInstructions.contains("Instructions"));
        assertFalse(sanitizedInstructions.contains("<iframe>"));

        String sanitizedPrompt = jdbc.queryForObject(
                "select prompt from public.test_version_questions where test_version_id = ?", String.class, versionId);
        assertTrue(sanitizedPrompt.contains("Question prompt"));
        assertFalse(sanitizedPrompt.contains("onerror"));

        String sanitizedSnapshot = jdbc.queryForObject(
                "select builder_content::text from public.test_versions where id = ?", String.class, versionId);
        assertFalse(sanitizedSnapshot.contains("<script>"));
        assertFalse(sanitizedSnapshot.contains("<iframe"));
        assertFalse(sanitizedSnapshot.contains("onerror"));
    }

    @Test
    void publishRollsBackVersionAndStatusWhenMaterializationFails() {
        var created = createSingleReading("Rollback Test", createValidReadingBuilderContent(1, 5));
        doThrow(new BusinessRuleException("simulated materialization failure"))
                .when(readingVersionMaterializer).materialize(any(), any());

        assertThrows(BusinessRuleException.class, () ->
                testBankService.changeStatus(created.id(), "PUBLISHED", created.draftRevision(), actorId, true));

        var afterFailure = testBankService.get(created.id());
        assertEquals("DRAFT", afterFailure.status());
        assertNull(afterFailure.publishedVersion());
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from public.test_versions where test_id = ?", Integer.class, created.id()));
    }

    @Test
    void assignmentAndStudentAttemptRemainPinnedToPublishedVersionOne() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = createCourse();
        jdbc.update("insert into public.profiles(id, email, full_name) values (?, ?, ?)",
                studentId, "student-" + studentId + "@example.test", "Reading Student");
        jdbc.update("insert into public.student_profiles(user_id, student_code) values (?, ?)",
                studentId, "ST-" + studentId.toString().substring(0, 8));

        var v1Content = createValidReadingBuilderContent(1, 5);
        var created = createSingleReading("Pinned Reading v1", v1Content);
        var publishedV1 = testBankService.changeStatus(
                created.id(), "PUBLISHED", created.draftRevision(), actorId, true);
        UUID versionOneId = publishedV1.publishedVersion().id();

        var assignment = assignmentService.create(new CreateTestAssignmentRequest(
                versionOneId, courseId, null, null, (short) 1, "PRACTICE", 900, true
        ), actorId, true);

        var revision = testBankService.createRevision(
                created.id(), publishedV1.draftRevision(), actorId, true);
        var v2Content = createValidReadingBuilderContent(1, 5);
        passages(v2Content).getFirst().put("content", "<p>VERSION TWO ONLY</p>");
        var updated = testBankService.update(created.id(), new TestBankRequest(
                "Pinned Reading v2", "Version two", SkillType.READING, "SINGLE_SKILL", 15,
                "v2.0-draft", List.of("READING"), v2Content, revision.draftRevision()
        ), actorId, true);
        var publishedV2 = testBankService.changeStatus(
                created.id(), "PUBLISHED", updated.draftRevision(), actorId, true);
        assertFalse(versionOneId.equals(publishedV2.publishedVersion().id()));

        var attempt = studentDeliveryService.startOrResume(assignment.id(), studentId);
        assertEquals(versionOneId, attempt.testVersionId());
        assertEquals("Pinned Reading v1", attempt.title());
        assertTrue(attempt.sections().getFirst().get("contentHtml").toString().contains("authentic passage text"));
        assertFalse(attempt.sections().getFirst().get("contentHtml").toString().contains("VERSION TWO ONLY"));

        @SuppressWarnings("unchecked")
        var groups = (List<Map<String, Object>>) attempt.sections().getFirst().get("questionGroups");
        @SuppressWarnings("unchecked")
        var questions = (List<Map<String, Object>>) groups.getFirst().get("questions");
        assertFalse(questions.getFirst().containsKey("correctAnswers"));
    }

    @Test
    void publishedReadingAppearsInSelfPracticeCatalogAndResumesOneAttempt() {
        UUID studentId = UUID.randomUUID();
        jdbc.update("insert into public.profiles(id, email, full_name) values (?, ?, ?)",
                studentId, "self-practice-" + studentId + "@example.test", "Self Practice Student");
        jdbc.update("insert into public.student_profiles(user_id, student_code) values (?, ?)",
                studentId, "SP-" + studentId.toString().substring(0, 8));

        var draftOnly = createSingleReading("Draft must stay hidden", createValidReadingBuilderContent(1, 5));
        var publishable = createSingleReading("Published self practice", createValidReadingBuilderContent(1, 5));
        var published = testBankService.changeStatus(
                publishable.id(), "PUBLISHED", publishable.draftRevision(), actorId, true);

        var catalog = studentDeliveryService.listPublishedTests(studentId);
        assertTrue(catalog.stream().noneMatch(item -> item.testId().equals(draftOnly.id())));
        var item = catalog.stream().filter(value -> value.testId().equals(publishable.id())).findFirst().orElseThrow();
        assertEquals(published.publishedVersion().id(), item.testVersionId());
        assertEquals(1, item.sectionsCount());
        assertEquals(5, item.totalQuestions());
        assertEquals(0, item.attemptsCount());

        var first = studentDeliveryService.startOrResumeSelfPractice(item.testVersionId(), studentId);
        var resumed = studentDeliveryService.startOrResumeSelfPractice(item.testVersionId(), studentId);
        assertEquals(first.attemptId(), resumed.attemptId());
        assertNull(first.assignmentId());
        assertEquals("SELF_PRACTICE", jdbc.queryForObject(
                "select attempt_origin from public.test_attempts where id = ?", String.class, first.attemptId()));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from public.test_attempts
                where student_id = ? and test_version_id = ? and attempt_origin = 'SELF_PRACTICE'
                """, Integer.class, studentId, item.testVersionId()));
    }

    private TestBankResponse createSingleReading(String title, Map<String, Object> content) {
        return testBankService.create(new TestBankRequest(
                title, "Description", SkillType.READING, "SINGLE_SKILL", 15,
                "v1.0-draft", List.of("READING"), content, null
        ), actorId);
    }

    private UUID createCourse() {
        UUID courseId = UUID.randomUUID();
        jdbc.update("""
                insert into public.courses(
                  id, code, name, capacity, starts_on, status, created_by, skill_pair, total_sessions
                ) values (?, ?, ?, 20, current_date, 'ACTIVE', ?, 'LISTENING_READING', 12)
                """, courseId, "COURSE-" + courseId.toString().substring(0, 8),
                "Reading Test Course", actorId);
        return courseId;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> passages(Map<String, Object> content) {
        return (List<Map<String, Object>>) content.get("passages");
    }

    private Map<String, Object> createValidReadingBuilderContent(int numPassages, int totalQuestions) {
        int questionsPerPassage = totalQuestions / numPassages;
        int remaining = totalQuestions % numPassages;
        int currentQuestionNo = 1;

        var passages = new ArrayList<Map<String, Object>>();
        for (int p = 1; p <= numPassages; p++) {
            int qCount = questionsPerPassage + (p == numPassages ? remaining : 0);
            var passage = new LinkedHashMap<String, Object>();
            passage.put("id", "passage-id-" + p);
            passage.put("passageNo", p);
            passage.put("title", "Reading Passage " + p);
            passage.put("content", "<p>This is authentic passage text for section " + p + ".</p>");

            var group = new LinkedHashMap<String, Object>();
            group.put("id", "group-id-" + p);
            group.put("title", "Questions " + currentQuestionNo + "–" + (currentQuestionNo + qCount - 1));
            group.put("typeFormat", "MULTIPLE_CHOICE");
            group.put("instructions", "<p>Choose the correct letter A, B, C, or D.</p>");

            var questions = new ArrayList<Map<String, Object>>();
            for (int q = 0; q < qCount; q++) {
                var question = new LinkedHashMap<String, Object>();
                question.put("id", "q-id-" + currentQuestionNo);
                question.put("number", currentQuestionNo);
                question.put("typeFormat", "MULTIPLE_CHOICE");
                question.put("prompt", "<p>Question prompt number " + currentQuestionNo + "</p>");
                question.put("correctAnswers", List.of("opt-a-" + currentQuestionNo));
                question.put("options", List.of(
                        Map.of("id", "opt-a-" + currentQuestionNo, "code", "A", "text", "Choice A"),
                        Map.of("id", "opt-b-" + currentQuestionNo, "code", "B", "text", "Choice B"),
                        Map.of("id", "opt-c-" + currentQuestionNo, "code", "C", "text", "Choice C"),
                        Map.of("id", "opt-d-" + currentQuestionNo, "code", "D", "text", "Choice D")
                ));
                questions.add(question);
                currentQuestionNo++;
            }
            group.put("questions", questions);
            passage.put("questionGroups", List.of(group));
            passages.add(passage);
        }
        return Map.of("passages", passages);
    }
}
