package com.theieltsspells.studentportal.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StudentCourseWorkspaceResponse(
        CourseInfo course,
        EnrollmentInfo enrollment,
        NextAction nextAction,
        NextSession nextSession,
        List<SessionItem> sessions,
        List<ResourceItem> resources,
        SkillReport skills,
        List<StudyLogItem> studyLogs,
        List<WeaknessItem> weaknesses,
        GoalProgress goalProgress,
        List<TeacherFeedbackItem> teacherFeedback
) {
    public record CourseInfo(
            UUID id,
            String code,
            String name,
            String description,
            String level,
            String skillPair,
            BigDecimal targetBand,
            Short totalSessions,
            BigDecimal tuitionAmount,
            Short capacity,
            LocalDate startsOn,
            LocalDate endsOn,
            String status,
            String defaultZoomUrl,
            String primaryTeacherName
    ) {}

    public record EnrollmentInfo(
            UUID enrollmentId,
            String status,
            int completedSessions,
            int totalSessions,
            LocalDate plannedExamMonth,
            LocalDate actualExamDate,
            String examRegistrationStatus
    ) {}

    public record NextAction(
            String actionType,
            String title,
            String description,
            OffsetDateTime deadline,
            String priority,
            String ctaLabel,
            String ctaUrl,
            String contextBadge
    ) {}

    public record NextSession(
            UUID sessionId,
            Short sessionNo,
            String title,
            String phaseName,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String teacherName,
            String zoomUrl,
            List<String> prepMaterials,
            List<String> prerequisiteTasks
    ) {}

    public record SessionItem(
            UUID id,
            Short sessionNo,
            String title,
            String phaseName,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String status,
            String teacherName,
            String zoomUrl,
            List<AttachedMaterial> materials,
            List<AttachedAssignment> assignments
    ) {
        public record AttachedMaterial(
                UUID id,
                String title,
                String resourceType,
                String fileRole,
                String externalUrl
        ) {}

        public record AttachedAssignment(
                UUID id,
                String title,
                String status,
                BigDecimal score,
                BigDecimal maxScore
        ) {}
    }

    public record ResourceItem(
            UUID id,
            String code,
            String title,
            String skill,
            String resourceType,
            String fileRole,
            String externalUrl,
            String category,
            Short sessionNo
    ) {}

    public record SkillReport(
            BigDecimal overallBand,
            BigDecimal targetBand,
            Integer readingAccuracy,
            Integer listeningAccuracy,
            Integer writingBandScore,
            Integer speakingBandScore,
            int totalCompletedTests,
            int totalQuestionsAnswered
    ) {}

    public record StudyLogItem(
            UUID id,
            String testTitle,
            String skill,
            OffsetDateTime completedAt,
            BigDecimal score,
            BigDecimal maxScore,
            Integer correctCount,
            Integer totalQuestions,
            String source,
            String platform,
            String reviewUrl
    ) {}

    public record WeaknessItem(
            String questionType,
            int errorCount,
            String recommendation,
            String skill
    ) {}

    public record GoalProgress(
            int weeklyCompletedCount,
            int weeklyTarget,
            int onTimeRate,
            int sessionAttendanceRate,
            int currentStreakDays
    ) {}

    public record TeacherFeedbackItem(
            UUID id,
            String teacherName,
            OffsetDateTime reviewedAt,
            String status,
            BigDecimal verifiedScore,
            String feedback,
            boolean isRevisionRequired,
            String priority,
            boolean isRead
    ) {}
}
