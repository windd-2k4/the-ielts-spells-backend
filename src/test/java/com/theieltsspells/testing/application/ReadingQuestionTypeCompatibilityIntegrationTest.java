package com.theieltsspells.testing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.academic.application.AcademicMembershipService;
import com.theieltsspells.shared.persistence.enums.SkillType;
import com.theieltsspells.testing.application.dto.SaveReadingResponseItem;
import com.theieltsspells.testing.application.dto.SaveReadingResponsesRequest;
import com.theieltsspells.testing.application.dto.TestBankRequest;
import com.theieltsspells.testing.application.dto.TestBankResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract-level integration coverage for every supported Reading question type.
 *
 * <p>The test deliberately goes through the same path as production:
 * draft creation -> publication gate -> immutable materialization -> student
 * delivery -> response persistence -> submission and auto-grading.</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
class ReadingQuestionTypeCompatibilityIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("reading_question_type_compatibility")
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
            registry.add("spring.datasource.username",
                    () -> System.getProperty("reading.test.username", "reading_test"));
            registry.add("spring.datasource.password",
                    () -> System.getProperty("reading.test.password", "reading_test"));
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        }
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @org.junit.jupiter.api.AfterAll
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
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AcademicMembershipService membershipService;

    @ParameterizedTest(name = "{0}")
    @MethodSource("questionTypeCases")
    void eachQuestionTypePublishesDeliversAndGrades(QuestionTypeCase questionTypeCase) {
        UUID actorId = createProfile("author");
        UUID studentId = createStudentProfile();
        Map<String, Object> content = contentFor(questionTypeCase);

        TestBankResponse created = testBankService.create(new TestBankRequest(
                "Compatibility · " + questionTypeCase.testId(),
                "One-question Reading compatibility fixture",
                SkillType.READING,
                "SINGLE_SKILL",
                15,
                "compatibility-1.0",
                List.of("READING", "COMPATIBILITY"),
                content,
                null
        ), actorId);

        TestBankResponse published = testBankService.changeStatus(
                created.id(), "PUBLISHED", created.draftRevision(), actorId, true);
        assertEquals("PUBLISHED", published.status(), questionTypeCase.testId());
        assertNotNull(published.publishedVersion(), questionTypeCase.testId());
        UUID versionId = published.publishedVersion().id();

        assertEquals(questionTypeCase.type(), jdbc.queryForObject("""
                select type_format from public.test_version_questions
                where test_version_id = ? and question_key = 'compat-q-1'
                """, String.class, versionId));
        assertEquals(questionTypeCase.type(), jdbc.queryForObject("""
                select type_format from public.test_version_question_groups
                where test_version_id = ? and group_key = 'compat-group-1'
                """, String.class, versionId));

        int sharedOptionCount = jdbc.queryForObject("""
                select count(*) from public.test_version_question_options question_option
                join public.test_version_question_groups question_group on question_group.id = question_option.group_id
                where question_option.test_version_id = ? and question_group.group_key = 'compat-group-1'
                  and question_option.question_id is null
                """, Integer.class, versionId);
        assertEquals(questionTypeCase.sharedOptions() ? 2 : 0, sharedOptionCount,
                questionTypeCase.testId() + " shared option bank");

        String answerConfig = jdbc.queryForObject("""
                select answer_config::text from public.test_version_question_groups
                where test_version_id = ? and group_key = 'compat-group-1'
                """, String.class, versionId);
        assertNotNull(answerConfig);
        if (questionTypeCase.completion()) {
            assertTrue(answerConfig.contains("wordLimitRule") || questionTypeCase.optionBankCompletion(),
                    questionTypeCase.testId() + " word-limit/option-bank configuration");
        }
        if (questionTypeCase.optionBankCompletion()) {
            assertTrue(answerConfig.contains("OPTION_BANK"), questionTypeCase.testId() + " answer source");
        }

        var attempt = studentDeliveryService.startOrResumeSelfPractice(versionId, studentId);
        @SuppressWarnings("unchecked")
        var groups = (List<Map<String, Object>>) attempt.sections().getFirst().get("questionGroups");
        Map<String, Object> deliveredGroup = groups.getFirst();
        assertEquals(questionTypeCase.type(), deliveredGroup.get("typeFormat"),
                questionTypeCase.testId() + " delivered group type");

        @SuppressWarnings("unchecked")
        var deliveredQuestions = (List<Map<String, Object>>) deliveredGroup.get("questions");
        Map<String, Object> deliveredQuestion = deliveredQuestions.getFirst();
        assertEquals(questionTypeCase.type(), deliveredQuestion.get("typeFormat"),
                questionTypeCase.testId() + " delivered question type");
        assertFalse(deliveredQuestion.containsKey("correctAnswers"),
                questionTypeCase.testId() + " must not expose the answer key");

        @SuppressWarnings("unchecked")
        var deliveredSharedOptions = (List<Map<String, Object>>) deliveredGroup.get("sharedOptions");
        assertEquals(questionTypeCase.sharedOptions() ? 2 : 0, deliveredSharedOptions.size(),
                questionTypeCase.testId() + " delivered shared options");

        @SuppressWarnings("unchecked")
        var deliveredOptions = (List<Map<String, Object>>) deliveredQuestion.get("options");
        assertEquals(questionTypeCase.questionOptions() ? 4 : 0, deliveredOptions.size(),
                questionTypeCase.testId() + " delivered question options");

        var answer = objectMapper.valueToTree(questionTypeCase.answer());
        var saved = studentDeliveryService.saveResponses(
                attempt.attemptId(),
                new SaveReadingResponsesRequest(List.of(
                        new SaveReadingResponseItem("compat-q-1", answer, 1)
                )),
                studentId);
        assertEquals(1, saved.responses().size(), questionTypeCase.testId() + " saved response");

        var result = studentDeliveryService.submit(attempt.attemptId(), studentId);
        assertEquals(1, result.correctCount(), questionTypeCase.testId() + " correct count");
        assertEquals(0, result.incorrectCount(), questionTypeCase.testId() + " incorrect count");
        assertEquals(0, result.unansweredCount(), questionTypeCase.testId() + " unanswered count");
        var questionResult = result.questions().stream()
                .filter(item -> item.questionKey().equals("compat-q-1"))
                .findFirst()
                .orElseThrow();
        assertTrue(questionResult.answered(), questionTypeCase.testId() + " answered");
        assertTrue(Boolean.TRUE.equals(questionResult.correct()), questionTypeCase.testId() + " correctness");
    }

    static Stream<Arguments> questionTypeCases() {
        return Stream.of(
                Arguments.of(new QuestionTypeCase("Q01", "MULTIPLE_CHOICE", false, true,
                        false, false, List.of("q-opt-a"), Map.of("value", "q-opt-a"))),
                Arguments.of(new QuestionTypeCase("Q02", "MULTIPLE_ANSWERS", false, true,
                        false, false, List.of("q-opt-a", "q-opt-b"),
                        Map.of("values", List.of("q-opt-b", "q-opt-a")))),
                Arguments.of(new QuestionTypeCase("Q03", "TRUE_FALSE_NOT_GIVEN", false, false,
                        false, false, List.of("TRUE"), Map.of("value", "TRUE"))),
                Arguments.of(new QuestionTypeCase("Q04", "YES_NO_NOT_GIVEN", false, false,
                        false, false, List.of("YES"), Map.of("value", "YES"))),
                Arguments.of(new QuestionTypeCase("Q05", "MATCHING_HEADINGS", true, false,
                        false, false, List.of("shared-a"), Map.of("value", "shared-a"))),
                Arguments.of(new QuestionTypeCase("Q06", "MATCHING_INFORMATION", true, false,
                        false, false, List.of("shared-a"), Map.of("value", "shared-a"))),
                Arguments.of(new QuestionTypeCase("Q07", "MATCHING_FEATURES", true, false,
                        false, false, List.of("shared-a"), Map.of("value", "shared-a"))),
                Arguments.of(new QuestionTypeCase("Q08", "MATCHING_SENTENCE_ENDINGS", true, false,
                        false, false, List.of("shared-a"), Map.of("value", "shared-a"))),
                Arguments.of(new QuestionTypeCase("Q09", "FILL_IN_BLANK", false, false,
                        true, false, List.of("water"), Map.of("value", "water"))),
                Arguments.of(new QuestionTypeCase("Q10", "SHORT_ANSWER", false, false,
                        true, false, List.of("water"), Map.of("value", "water"))),
                Arguments.of(new QuestionTypeCase("Q11", "SENTENCE_COMPLETION", false, false,
                        true, false, List.of("water"), Map.of("value", "water"))),
                Arguments.of(new QuestionTypeCase("Q12", "SUMMARY_COMPLETION", false, false,
                        true, false, List.of("water"), Map.of("value", "water"))),
                Arguments.of(new QuestionTypeCase("Q13", "NOTE_COMPLETION", false, false,
                        true, false, List.of("water"), Map.of("value", "water"))),
                Arguments.of(new QuestionTypeCase("Q14", "TABLE_COMPLETION", false, false,
                        true, false, List.of("water"), Map.of("value", "water"))),
                Arguments.of(new QuestionTypeCase("Q15", "FLOW_CHART_COMPLETION", false, false,
                        true, false, List.of("water"), Map.of("value", "water"))),
                Arguments.of(new QuestionTypeCase("Q16", "DIAGRAM_LABELING", false, false,
                        true, false, List.of("water"), Map.of("value", "water"))),
                Arguments.of(new QuestionTypeCase("Q12-OB", "SUMMARY_COMPLETION", true, false,
                        true, true, List.of("shared-a"), Map.of("value", "shared-a")))
        );
    }

    private Map<String, Object> contentFor(QuestionTypeCase questionTypeCase) {
        var question = new LinkedHashMap<String, Object>();
        question.put("id", "compat-q-1");
        question.put("number", 1);
        question.put("typeFormat", questionTypeCase.type());
        question.put("prompt", "<p>Compatibility question for " + questionTypeCase.testId() + "</p>");
        question.put("correctAnswers", questionTypeCase.correctAnswers());
        question.put("acceptableAnswers", List.of());
        question.put("maxScore", 1);
        if (questionTypeCase.questionOptions()) {
            question.put("options", questionOptions());
        }

        var group = new LinkedHashMap<String, Object>();
        group.put("id", "compat-group-1");
        group.put("title", "Questions 1–1 · " + questionTypeCase.type());
        group.put("typeFormat", questionTypeCase.type());
        group.put("instructions", instructionFor(questionTypeCase));
        group.put("questions", List.of(question));
        if (questionTypeCase.sharedOptions()) {
            group.put("answerSource", "OPTION_BANK");
            group.put("allowOptionReused", false);
            group.put("sharedOptions", sharedOptions());
        } else if (questionTypeCase.completion()) {
            group.put("answerSource", questionTypeCase.optionBankCompletion() ? "OPTION_BANK" : "PASSAGE");
            if (!questionTypeCase.optionBankCompletion()) {
                group.put("wordLimitRule", "NO MORE THAN TWO WORDS");
                group.put("gapFillTemplate", "The answer is [[1]].");
            }
        }

        var passage = new LinkedHashMap<String, Object>();
        passage.put("id", "compat-passage-1");
        passage.put("passageNo", 1);
        passage.put("title", "Compatibility Passage");
        passage.put("content", "<p>The passage contains clean water for the answer.</p>");
        passage.put("questionGroups", List.of(group));
        return Map.of("passages", List.of(passage));
    }

    private String instructionFor(QuestionTypeCase questionTypeCase) {
        if (questionTypeCase.questionOptions()) {
            return "Choose the correct answer.";
        }
        if (questionTypeCase.sharedOptions()) {
            return "Choose the correct option from the list.";
        }
        if (questionTypeCase.type().equals("TRUE_FALSE_NOT_GIVEN")) {
            return "Choose TRUE, FALSE or NOT GIVEN.";
        }
        if (questionTypeCase.type().equals("YES_NO_NOT_GIVEN")) {
            return "Choose YES, NO or NOT GIVEN.";
        }
        return "Complete the answer using NO MORE THAN TWO WORDS.";
    }

    private List<Map<String, Object>> questionOptions() {
        return List.of(
                Map.of("id", "q-opt-a", "code", "A", "text", "Choice A"),
                Map.of("id", "q-opt-b", "code", "B", "text", "Choice B"),
                Map.of("id", "q-opt-c", "code", "C", "text", "Choice C"),
                Map.of("id", "q-opt-d", "code", "D", "text", "Choice D")
        );
    }

    private List<Map<String, Object>> sharedOptions() {
        return List.of(
                Map.of("id", "shared-a", "code", "A", "text", "Shared option A"),
                Map.of("id", "shared-b", "code", "B", "text", "Shared option B")
        );
    }

    private UUID createProfile(String prefix) {
        UUID profileId = UUID.randomUUID();
        jdbc.update("insert into public.profiles(id, email, full_name) values (?, ?, ?)",
                profileId, prefix + "-" + profileId + "@example.test", prefix);
        return profileId;
    }

    private UUID createStudentProfile() {
        UUID studentId = createProfile("compat-student");
        jdbc.update("insert into public.student_profiles(user_id, student_code) values (?, ?)",
                studentId, "CT-" + studentId.toString().substring(0, 8));
        return studentId;
    }

    private record QuestionTypeCase(
            String testId,
            String type,
            boolean sharedOptions,
            boolean questionOptions,
            boolean completion,
            boolean optionBankCompletion,
            List<String> correctAnswers,
            Map<String, Object> answer
    ) {
    }
}
