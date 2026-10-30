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
        List<DailyActivity> activityCalendar,
        List<ReadingAssignment> readingAssignments,
        List<CourseAssignment> courseAssignments,
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
            BigDecimal listeningTarget,
            BigDecimal readingTarget,
            BigDecimal writingTarget,
            BigDecimal speakingTarget,
            LocalDate joinedAt,
            LocalDate plannedExamDate,
            String examPlanStatus,
            boolean learningSetupComplete
    ) {
    }

    public record Metrics(
            int assignedTests,
            int pendingTests,
            int pendingCourseAssignments,
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
            OffsetDateTime nextSessionAt,
            LocalDate plannedExamMonth,
            LocalDate actualExamDate,
            String examRegistrationStatus,
            String coverImageUrl,
            String coverImageAltText
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
            String attemptOrigin,
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

    public record DailyActivity(
            LocalDate activityDate,
            int reading,
            int listening,
            int writing,
            int speaking,
            int totalAttempts,
            long durationSeconds
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

    public record CourseAssignment(
            UUID assignmentId,
            UUID classActivityId,
            UUID courseId,
            String courseCode,
            String courseName,
            Integer sessionNo,
            String sessionTitle,
            String title,
            String skill,
            OffsetDateTime dueAt,
            OffsetDateTime effectiveDueAt,
            OffsetDateTime extensionDueAt,
            String latePolicy,
            int lateGraceMinutes,
            boolean canEdit,
            boolean canSubmit,
            String lockReason,
            String status,
            String reviewStatus,
            BigDecimal score,
            BigDecimal maxScore
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
