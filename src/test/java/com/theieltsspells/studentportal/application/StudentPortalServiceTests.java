package com.theieltsspells.studentportal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
                    "joinedAt": "2026-01-01"
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
                  "readingAssignments": [],
                  "recommendedCourses": [],
                  "aiStatus": "DEVELOPMENT"
                }
                """.formatted(profileId));

        var result = service.overview(studentId);

        assertThat(result.profile().id()).isEqualTo(profileId);
        assertThat(result.metrics().assignedTests()).isEqualTo(1);
        assertThat(result.readingAssignments()).isEmpty();
        verify(jdbc).queryForObject(any(String.class), eq(String.class), eq(studentId));
    }

    @Test
    void rejectsTargetBandOutsideHalfBandSteps() {
        assertThatThrownBy(() -> service.updateTargetBand(UUID.randomUUID(), new BigDecimal("7.25")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Band mục tiêu phải theo bước 0.5");

        verify(jdbc, never()).update(any(String.class), any(), any());
    }

    @Test
    void rejectsTargetBandBelowCurrentBand() {
        UUID studentId = UUID.randomUUID();
        when(jdbc.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq(studentId)))
                .thenReturn(List.of(new BigDecimal("6.5")));

        assertThatThrownBy(() -> service.updateTargetBand(studentId, new BigDecimal("6.0")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Band mục tiêu không được thấp hơn Band hiện tại");
    }

    @Test
    void updatesAValidStudentTargetBand() {
        UUID studentId = UUID.randomUUID();
        when(jdbc.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq(studentId)))
                .thenReturn(List.of(new BigDecimal("6.0")));

        var result = service.updateTargetBand(studentId, new BigDecimal("7.0"));

        assertThat(result.currentBand()).isEqualByComparingTo("6.0");
        assertThat(result.targetBand()).isEqualByComparingTo("7.0");
        verify(jdbc).update(any(String.class), eq(new BigDecimal("7.0")), eq(studentId));
    }

    @Test
    void acceptsAValidTargetBandWithExtraDecimalPlaces() {
        UUID studentId = UUID.randomUUID();
        when(jdbc.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq(studentId)))
                .thenReturn(List.of(new BigDecimal("6.0")));

        var result = service.updateTargetBand(studentId, new BigDecimal("7.00"));

        assertThat(result.targetBand()).isEqualByComparingTo("7.0");
        verify(jdbc).update(any(String.class), eq(new BigDecimal("7.00")), eq(studentId));
    }
}
