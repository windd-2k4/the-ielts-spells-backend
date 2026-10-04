package com.theieltsspells.reporting.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminDashboardServiceIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("admin_dashboard")
            .withUsername("dashboard_test")
            .withPassword("dashboard_test");

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
    private AdminDashboardService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void dashboardUsesRecordedAttendanceAndRealRoutes() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        jdbc.update("insert into public.profiles(id, full_name, email) values (?, 'Dashboard Student', 'dashboard@example.test')",
                studentId);
        jdbc.update("""
                insert into public.student_profiles(user_id, student_code, current_band, target_band)
                values (?, 'HV-DASHBOARD', 5.0, 6.5)
                """, studentId);
        jdbc.update("""
                insert into public.courses(id, code, name, capacity, starts_on, status, skill_pair, total_sessions)
                values (?, 'DASH-01', 'Dashboard Course', 20, ?, 'ACTIVE', 'LISTENING_READING', 3)
                """, courseId, LocalDate.now().minusDays(7));
        jdbc.update("""
                insert into public.enrollments(course_id, student_id, status, enrolled_at)
                values (?, ?, 'ACTIVE', now() - interval '30 days')
                """, courseId, studentId);

        for (int index = 0; index < 3; index++) {
            UUID sessionId = UUID.randomUUID();
            OffsetDateTime startsAt = OffsetDateTime.now().minusDays(3L - index);
            jdbc.update("""
                    insert into public.course_sessions(id, course_id, session_no, title, starts_at, ends_at, status)
                    values (?, ?, ?, ?, ?, ?, 'COMPLETED')
                    """, sessionId, courseId, (short) (index + 1), "Session " + (index + 1), startsAt,
                    startsAt.plusHours(2));
            jdbc.update("""
                    insert into public.attendance_records(session_id, student_id, status, is_confirmed)
                    values (?, ?, cast(? as public.attendance_status), true)
                    """, sessionId, studentId, index == 0 ? "PRESENT" : "ABSENT");
        }

        var dashboard = service.overview("7d", courseId);

        assertThat(dashboard.kpis().activeStudents().value()).isEqualTo(1);
        assertThat(dashboard.kpis().averageAttendanceRate().formattedValue()).isEqualTo("33%");
        assertThat(dashboard.kpis().pendingLeads().targetRoute()).isEqualTo("/students?tab=leads");
        assertThat(dashboard.kpis().pendingGradingCount().targetRoute()).isEqualTo("/writing-evaluations");
        assertThat(dashboard.trend().points()).hasSize(3)
                .allSatisfy(point -> assertThat(point.attendanceRate()).isIn(0.0, 100.0));
        assertThat(dashboard.atRiskStudents()).singleElement()
                .satisfies(student -> {
                    assertThat(student.missedSessions()).isEqualTo(2);
                    assertThat(student.riskReason()).isEqualTo("2 buổi vắng");
                });
        assertThat(dashboard.coursePerformances()).singleElement()
                .satisfies(course -> assertThat(course.attendanceRate()).isEqualTo(33));
    }
}
