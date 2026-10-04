package com.theieltsspells.reporting.application.dto;

import com.theieltsspells.shared.persistence.enums.ClassStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminDashboardResponse(
        // Backward compatibility fields
        long activeEnrollments,
        long openCourses,
        long activeCourses,
        long totalCourses,
        List<UpcomingCourse> upcomingCourses,

        // Upgraded Full Operational Structure
        ExecutiveKpis kpis,
        List<ActionItem> actionItems,
        LearningTrend trend,
        List<AtRiskStudent> atRiskStudents,
        List<ScheduleEvent> scheduleEvents,
        List<CoursePerformance> coursePerformances,
        List<CourseOption> courseFilterOptions,
        OffsetDateTime updatedAt
) {
    public record UpcomingCourse(
            UUID id,
            String code,
            String name,
            short capacity,
            LocalDate startsOn,
            ClassStatus status
    ) {
    }

    public record ExecutiveKpis(
            KpiMetric activeStudents,
            KpiMetric pendingLeads,
            KpiMetric leadConversionRate,
            KpiMetric averageAttendanceRate,
            KpiMetric pendingGradingCount,
            KpiMetric overdueInvoicesCount
    ) {
    }

    public record KpiMetric(
            double value,
            String formattedValue,
            Double previousValue,
            Double changePercent,
            String changeType, // "INCREASE", "DECREASE", "STABLE"
            String tooltip,
            String targetRoute
    ) {
    }

    public record ActionItem(
            String id,
            String type, // "NEW_LEAD", "PENDING_GRADING", "UNASSIGNED_TEACHER", "OVERDUE_ORDER", "AT_RISK_STUDENT"
            String title,
            String description,
            int count,
            String priority, // "HIGH", "MEDIUM", "LOW"
            String deadlineNote,
            String ctaLabel,
            String ctaRoute
    ) {
    }

    public record LearningTrend(
            List<TrendPoint> points,
            Double benchmarkTargetScore
    ) {
    }

    public record TrendPoint(
            String dateLabel,
            LocalDate date,
            Double averageScore,
            int completedAttempts,
            Double attendanceRate,
            Double readingScore,
            Double listeningScore,
            Double writingScore,
            Double speakingScore
    ) {
    }

    public record AtRiskStudent(
            UUID studentId,
            String studentCode,
            String fullName,
            String courseName,
            String courseCode,
            String riskReason,
            String riskLevel, // "HIGH", "MEDIUM"
            Double currentBand,
            Double targetBand,
            int missedSessions,
            int pendingAssignments,
            String profileRoute
    ) {
    }

    public record ScheduleEvent(
            String id,
            String eventType, // "COURSE_START", "CLASS_SESSION", "TEST_DEADLINE"
            String title,
            String courseName,
            String courseCode,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String teacherName,
            String locationOrZoom,
            String statusBadge
    ) {
    }

    public record CoursePerformance(
            UUID courseId,
            String courseCode,
            String courseName,
            int currentEnrollments,
            int capacity,
            Integer attendanceRate,
            int completionProgress,
            Double averageScore,
            int atRiskCount,
            String operationalStatus
    ) {
    }

    public record CourseOption(
            UUID id,
            String code,
            String name
    ) {
    }
}
