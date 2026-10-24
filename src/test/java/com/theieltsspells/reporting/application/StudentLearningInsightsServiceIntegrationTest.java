package com.theieltsspells.reporting.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class StudentLearningInsightsServiceIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("student_learning_insights")
            .withUsername("insights_test")
            .withPassword("insights_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private StudentLearningInsightsService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void countsCompletedAttemptsByVietnamMonthBeyondRecentAttemptLimit() {
        UUID studentId = UUID.randomUUID();
        UUID publisherId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        ZoneId vietnam = ZoneId.of("Asia/Ho_Chi_Minh");
        OffsetDateTime monthStart = LocalDate.now(vietnam).withDayOfMonth(1).atStartOfDay(vietnam).toOffsetDateTime();
        OffsetDateTime currentMonth = monthStart.plusMinutes(30).withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime previousMonth = monthStart.minusMinutes(30).withOffsetSameInstant(ZoneOffset.UTC);

        jdbc.update("insert into public.profiles(id, full_name, email) values (?, 'Monthly student', ?)",
                studentId, studentId + "@example.test");
        jdbc.update("insert into public.profiles(id, full_name, email) values (?, 'Monthly publisher', ?)",
                publisherId, publisherId + "@example.test");
        jdbc.update("insert into public.student_profiles(user_id, student_code) values (?, ?)",
                studentId, "HV-" + studentId.toString().substring(0, 8));
        jdbc.update("""
                insert into public.tests(id, title, duration_minutes, status, created_by, primary_skill, test_type)
                values (?, 'Monthly reading', 60, 'PUBLISHED', ?, 'READING', 'SINGLE_SKILL')
                """, testId, publisherId);
        jdbc.update("""
                insert into public.test_versions(
                  id, test_id, version_number, version_label, title, duration_minutes,
                  primary_skill, test_type, published_by
                ) values (?, ?, 1, 'v1', 'Monthly reading', 60, 'READING', 'SINGLE_SKILL', ?)
                """, versionId, testId, publisherId);

        for (short attemptNo = 1; attemptNo <= 52; attemptNo++) {
            jdbc.update("""
                    insert into public.test_attempts(
                      test_version_id, student_id, attempt_no, status, attempt_origin,
                      started_at, submitted_at, expires_at, last_saved_at
                    ) values (?, ?, ?, 'SUBMITTED', 'SELF_PRACTICE', ?, ?, ?, ?)
                    """, versionId, studentId, attemptNo, currentMonth.minusMinutes(20), currentMonth,
                    currentMonth.plusMinutes(40), currentMonth);
        }
        jdbc.update("""
                insert into public.test_attempts(
                  test_version_id, student_id, attempt_no, status, attempt_origin,
                  started_at, expires_at, last_saved_at
                ) values (?, ?, 53, 'EXPIRED', 'SELF_PRACTICE', ?, ?, ?)
                """, versionId, studentId, previousMonth.minusMinutes(20),
                previousMonth.plusMinutes(10), previousMonth);
        jdbc.update("""
                insert into public.test_attempts(
                  test_version_id, student_id, attempt_no, status, attempt_origin,
                  started_at, expires_at, last_saved_at
                ) values (?, ?, 54, 'IN_PROGRESS', 'SELF_PRACTICE', ?, ?, ?)
                """, versionId, studentId, currentMonth, currentMonth.plusMinutes(60), currentMonth);

        var insights = service.get(studentId);
        assertThat(insights.attempts()).hasSize(50);
        assertThat(insights.monthlyCompletedAttempts()).hasSize(24);
        assertThat(insights.monthlyCompletedAttempts().stream()
                .filter(item -> item.skill().equals("READING") && item.month().equals(YearMonth.from(monthStart).toString()))
                .findFirst().orElseThrow().completedAttempts()).isEqualTo(52);
        assertThat(insights.monthlyCompletedAttempts().stream()
                .filter(item -> item.skill().equals("READING") && item.month().equals(YearMonth.from(monthStart.minusMonths(1)).toString()))
                .findFirst().orElseThrow().completedAttempts()).isEqualTo(1);
        assertThat(insights.monthlyCompletedAttempts().stream()
                .filter(item -> item.skill().equals("WRITING"))
                .allMatch(item -> item.completedAttempts() == 0)).isTrue();
    }

    @Test
    void includesRecordedTestAttemptTimeInStudyMinutes() {
        UUID studentId = UUID.randomUUID();
        UUID publisherId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        OffsetDateTime submittedAt = OffsetDateTime.now().minusHours(1);

        jdbc.update("insert into public.profiles(id, full_name, email) values (?, 'Student', ?)",
                studentId, studentId + "@example.test");
        jdbc.update("insert into public.profiles(id, full_name, email) values (?, 'Publisher', ?)",
                publisherId, publisherId + "@example.test");
        jdbc.update("insert into public.student_profiles(user_id, student_code) values (?, ?)",
                studentId, "HV-" + studentId.toString().substring(0, 8));
        jdbc.update("""
                insert into public.tests(id, title, duration_minutes, status, created_by, primary_skill, test_type)
                values (?, 'Reading practice', 60, 'PUBLISHED', ?, 'READING', 'SINGLE_SKILL')
                """, testId, publisherId);
        jdbc.update("""
                insert into public.test_versions(
                  id, test_id, version_number, version_label, title, duration_minutes,
                  primary_skill, test_type, published_by
                ) values (?, ?, 1, 'v1', 'Reading practice', 60, 'READING', 'SINGLE_SKILL', ?)
                """, versionId, testId, publisherId);
        jdbc.update("""
                insert into public.test_attempts(
                  test_version_id, student_id, attempt_no, status, attempt_origin,
                  started_at, submitted_at, expires_at, last_saved_at
                ) values (?, ?, 1, 'SUBMITTED', 'SELF_PRACTICE', ?, ?, ?, ?)
                """, versionId, studentId, submittedAt.minusMinutes(25), submittedAt,
                submittedAt.plusMinutes(35), submittedAt);

        var insights = service.get(studentId);

        assertThat(insights.summary().completedAttempts()).isEqualTo(1);
        assertThat(insights.summary().totalStudyMinutes()).isEqualTo(25);
    }

    @Test
    void returnsRealQuestionAnswersAndDurationForAnAttempt() {
        UUID studentId = UUID.randomUUID();
        UUID publisherId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        OffsetDateTime submittedAt = OffsetDateTime.now().minusMinutes(10);

        jdbc.update("insert into public.profiles(id, full_name, email) values (?, 'Student detail', ?)",
                studentId, studentId + "@example.test");
        jdbc.update("insert into public.profiles(id, full_name, email) values (?, 'Publisher detail', ?)",
                publisherId, publisherId + "@example.test");
        jdbc.update("insert into public.student_profiles(user_id, student_code) values (?, ?)",
                studentId, "HV-" + studentId.toString().substring(0, 8));
        jdbc.update("""
                insert into public.tests(id, title, duration_minutes, status, created_by, primary_skill, test_type)
                values (?, 'Reading detail', 60, 'PUBLISHED', ?, 'READING', 'SINGLE_SKILL')
                """, testId, publisherId);
        jdbc.update("""
                insert into public.test_versions(
                  id, test_id, version_number, version_label, title, duration_minutes,
                  primary_skill, test_type, published_by
                ) values (?, ?, 1, 'v1', 'Reading detail', 60, 'READING', 'SINGLE_SKILL', ?)
                """, versionId, testId, publisherId);
        jdbc.update("""
                insert into public.test_version_sections(
                  id, test_version_id, section_key, section_no, title, content_html, display_order
                ) values (?, ?, 'passage-1', 1, 'Reading Passage 1', '<p>The complete reading passage.</p>', 0)
                """, sectionId, versionId);
        jdbc.update("""
                insert into public.test_version_question_groups(
                  id, test_version_id, section_id, group_key, title, type_format, instructions, display_order
                ) values (?, ?, ?, 'matching', 'Matching headings', 'MATCHING', 'Match each paragraph', 0)
                """, groupId, versionId, sectionId);
        jdbc.update("""
                insert into public.test_version_questions(
                  test_version_id, group_id, question_key, question_no, type_format, prompt,
                  correct_answers, acceptable_answers, explanation, max_score, display_order
                ) values
                  (?, ?, 'q1', 1, 'MATCHING', 'Paragraph A', '["B"]'::jsonb, '[]'::jsonb, 'B is the matching heading.', 1, 0),
                  (?, ?, 'q2', 2, 'MATCHING', 'Paragraph B', '["C"]'::jsonb, '[]'::jsonb, 'C is the matching heading.', 1, 1)
                """, versionId, groupId, versionId, groupId);
        jdbc.update("""
                insert into public.test_attempts(
                  id, test_version_id, student_id, attempt_no, status, attempt_origin,
                  started_at, submitted_at, expires_at, last_saved_at, auto_score
                ) values (?, ?, ?, 1, 'GRADED', 'SELF_PRACTICE', ?, ?, ?, ?, 1)
                """, attemptId, versionId, studentId, submittedAt.minusMinutes(25), submittedAt,
                submittedAt.plusMinutes(35), submittedAt);
        jdbc.update("""
                insert into public.test_attempt_responses(
                  attempt_id, question_key, question_type, answer, normalized_answer,
                  is_correct, auto_score, max_score
                ) values (?, 'q1', 'MATCHING', '{"values":["B"]}'::jsonb, '["B"]'::jsonb, true, 1, 1)
                """, attemptId);

        var detail = service.getAttemptDetail(studentId, attemptId);

        assertThat(detail.durationSeconds()).isEqualTo(25 * 60);
        assertThat(detail.correctCount()).isEqualTo(1);
        assertThat(detail.incorrectCount()).isZero();
        assertThat(detail.unansweredCount()).isEqualTo(1);
        assertThat(detail.maxScore()).isEqualByComparingTo("2");
        assertThat(detail.questions()).hasSize(2);
        assertThat(detail.questions().getFirst().studentAnswers()).containsExactly("B");
        assertThat(detail.questions().getFirst().correctAnswers()).containsExactly("B");
        assertThat(detail.questions().getFirst().correct()).isTrue();
        assertThat(detail.questions().getFirst().explanation()).isEqualTo("B is the matching heading.");
        assertThat(detail.questions().get(1).studentAnswers()).isEmpty();
        assertThat(detail.sections()).hasSize(1);
        assertThat(detail.sections().getFirst().sectionNo()).isEqualTo(1);
        assertThat(detail.sections().getFirst().contentHtml()).isEqualTo("<p>The complete reading passage.</p>");
    }
}
