package com.theieltsspells.studentportal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentPortalServiceTests {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StudentPortalService service = new StudentPortalService(
            jdbc,
            new ObjectMapper().findAndRegisterModules()
    );

    @Test
    void loadsTheWholeOverviewWithOneDatabaseRoundTrip() {
        UUID studentId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        when(jdbc.queryForObject(any(String.class), eq(String.class), eq(studentId))).thenReturn("""
                {
                  "profile": {
                    "id": "%s",
                    "studentCode": "STU-001",
                    "fullName": "Nguyen Van A",
                    "email": null,
                    "phone": null,
                    "avatarPath": null,
                    "currentBand": 6.0,
                    "targetBand": 7.0,
                    "listeningTarget": 7.5,
                    "readingTarget": 7.0,
                    "writingTarget": 6.5,
                    "speakingTarget": 7.0,
                    "joinedAt": "2026-01-01",
                    "plannedExamDate": null,
                    "examPlanStatus": "NONE",
                    "learningSetupComplete": true
                  },
                  "metrics": {
                    "assignedTests": 1,
                    "pendingTests": 1,
                    "completedAttempts": 0,
                    "currentStreakDays": 0,
                    "lastActivityAt": null
                  },
                  "enrollments": [],
                  "upcomingSessions": [],
                  "recentAttempts": [],
                  "activityCalendar": [{
                    "activityDate": "2026-01-02",
                    "reading": 1,
                    "listening": 0,
                    "writing": 0,
                    "speaking": 0,
                    "totalAttempts": 1,
                    "durationSeconds": 900
                  }],
                  "readingAssignments": [],
                  "recommendedCourses": [],
                  "aiStatus": "DEVELOPMENT"
                }
                """.formatted(profileId));

        var result = service.overview(studentId);

        assertThat(result.profile().id()).isEqualTo(profileId);
        assertThat(result.profile().learningSetupComplete()).isTrue();
        assertThat(result.profile().examPlanStatus()).isEqualTo("NONE");
        assertThat(result.metrics().assignedTests()).isEqualTo(1);
        assertThat(result.activityCalendar()).singleElement()
                .satisfies(activity -> {
                    assertThat(activity.activityDate()).isEqualTo(java.time.LocalDate.of(2026, 1, 2));
                    assertThat(activity.reading()).isEqualTo(1);
                    assertThat(activity.totalAttempts()).isEqualTo(1);
                    assertThat(activity.durationSeconds()).isEqualTo(900);
                });
        assertThat(result.readingAssignments()).isEmpty();
        verify(jdbc).queryForObject(any(String.class), eq(String.class), eq(studentId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadsActivityForTheSelectedCalendarMonth() {
        UUID studentId = UUID.randomUUID();
        var expected = List.of(new com.theieltsspells.studentportal.application.dto.StudentPortalOverviewResponse.DailyActivity(
                LocalDate.of(2026, 9, 14), 2, 0, 1, 0, 3, 3_600
        ));
        when(jdbc.query(
                any(String.class),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq(studentId),
                eq(LocalDate.of(2026, 9, 1)),
                eq(LocalDate.of(2026, 10, 1))
        )).thenReturn(expected);

        var result = service.activity(studentId, YearMonth.of(2026, 9));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void rejectsCourseSessionsWhenTheStudentIsNotEnrolled() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(jdbc.queryForObject(any(String.class), eq(Boolean.class), eq(studentId), eq(courseId)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.courseSessions(studentId, courseId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy khóa học trong danh sách ghi danh");
    }

    @Test
    void rejectsTargetBandOutsideHalfBandSteps() {
        assertThatThrownBy(() -> service.updateTargetBand(
                UUID.randomUUID(), new BigDecimal("7.25"), null, null, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Band mục tiêu phải theo bước 0.5");

        verify(jdbc, never()).update(any(String.class), any(), any());
    }

    @Test
    void rejectsSkillTargetOutsideHalfBandSteps() {
        assertThatThrownBy(() -> service.updateTargetBand(
                UUID.randomUUID(), new BigDecimal("7.0"), new BigDecimal("7.25"), null, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Mục tiêu Listening phải theo bước 0.5");
    }

    @Test
    void rejectsTargetBandBelowCurrentBand() {
        UUID studentId = UUID.randomUUID();
        when(jdbc.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq(studentId)))
                .thenReturn(List.of(new BigDecimal("6.5")));

        assertThatThrownBy(() -> service.updateTargetBand(
                studentId, new BigDecimal("6.0"), null, null, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Band mục tiêu không được thấp hơn Band hiện tại");
    }

    @Test
    void updatesAValidStudentTargetBand() {
        UUID studentId = UUID.randomUUID();
        when(jdbc.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq(studentId)))
                .thenReturn(List.of(new BigDecimal("6.0")));

        var target = new BigDecimal("7.0");
        var result = service.updateTargetBand(studentId, target, target, target, target, target);

        assertThat(result.currentBand()).isEqualByComparingTo("6.0");
        assertThat(result.targetBand()).isEqualByComparingTo("7.0");
        assertThat(result.listeningTarget()).isEqualByComparingTo("7.0");
        verify(jdbc).update(any(String.class), eq(target), eq(target), eq(target), eq(target), eq(target), eq(studentId));
    }

    @Test
    void acceptsAValidTargetBandWithExtraDecimalPlaces() {
        UUID studentId = UUID.randomUUID();
        when(jdbc.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq(studentId)))
                .thenReturn(List.of(new BigDecimal("6.0")));

        var result = service.updateTargetBand(studentId, new BigDecimal("7.00"), null, null, null, null);

        assertThat(result.targetBand()).isEqualByComparingTo("7.0");
        verify(jdbc).update(any(String.class), eq(new BigDecimal("7.00")), isNull(), isNull(), isNull(), isNull(), eq(studentId));
    }

    @Test
    void rejectsAnExamDateInThePast() {
        assertThatThrownBy(() -> service.updateExamPlan(UUID.randomUUID(), false, LocalDate.of(2020, 1, 1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Ngày dự định thi không được ở trong quá khứ");
    }

    @Test
    void savesAnExplicitNoExamPlanChoice() {
        UUID studentId = UUID.randomUUID();
        when(jdbc.update(any(String.class), isNull(), eq("NONE"), eq(studentId))).thenReturn(1);
        when(jdbc.queryForObject(any(String.class), eq(Boolean.class), eq(studentId))).thenReturn(true);

        var result = service.updateExamPlan(studentId, true, null);

        assertThat(result.noExamPlan()).isTrue();
        assertThat(result.plannedExamDate()).isNull();
        assertThat(result.learningSetupComplete()).isTrue();
    }
}
