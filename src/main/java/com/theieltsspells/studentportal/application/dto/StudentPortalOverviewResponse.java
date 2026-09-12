package com.theieltsspells.studentportal.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StudentPortalOverviewResponse(
        Profile profile,
        Metrics metrics,
        List<Enrollment> enrollments,
        List<UpcomingSession> upcomingSessions,
        List<RecentAttempt> recentAttempts,
        List<ReadingAssignment> readingAssignments,
        List<CourseRecommendation> recommendedCourses,
        String aiStatus
) {
    public record Profile(
            UUID id,
            String studentCode,
            String fullName,
            String email,
            String phone,
            String avatarPath,
            BigDecimal currentBand,
            BigDecimal targetBand,
            LocalDate joinedAt
    ) {
    }

    public record Metrics(
            int assignedTests,
            int pendingTests,
            int completedAttempts,
            int currentStreakDays,
            OffsetDateTime lastActivityAt
    ) {
    }

    public record Enrollment(
            UUID enrollmentId,
            String status,
            UUID courseId,
            String courseCode,
            String courseName,
            String description,
            String level,
            String skillPair,
            BigDecimal courseTargetBand,
            LocalDate startsOn,
            LocalDate endsOn,
            int completedSessions,
            int totalSessions,
            String primaryTeacherName,
            OffsetDateTime nextSessionAt
    ) {
    }

    public record UpcomingSession(
            UUID sessionId,
            UUID courseId,
            String courseCode,
            String courseName,
            int sessionNo,
            String title,
            String phaseName,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String status,
            String teacherName,
            String zoomUrl
    ) {
    }

    public record RecentAttempt(
            UUID attemptId,
            UUID assignmentId,
            String title,
            String skill,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime submittedAt,
            BigDecimal score,
            BigDecimal maxScore,
            Integer correctCount,
            Integer totalQuestions
    ) {
    }

    public record ReadingAssignment(
            UUID assignmentId,
            UUID testVersionId,
            String title,
            String versionLabel,
            UUID courseId,
            String courseName,
            String mode,
            OffsetDateTime opensAt,
            OffsetDateTime closesAt,
            short maxAttempts,
            int attemptsUsed,
            Integer durationSeconds,
            OffsetDateTime activeAttemptExpiresAt
    ) {
    }

    public record CourseRecommendation(
            UUID courseId,
            String code,
            String name,
            String description,
            String level,
            String skillPair,
            BigDecimal targetBand,
            LocalDate startsOn,
            LocalDate endsOn,
            String reason
    ) {
    }
}
