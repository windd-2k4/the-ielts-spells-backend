package com.theieltsspells.reporting.application.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TeacherDashboardResponse(
        String teacherName,
        int pendingCount,
        int revisionCount,
        List<CourseSummary> courses,
        List<EvaluationQueueItem> evaluationQueue,
        OffsetDateTime updatedAt
) {
    public record CourseSummary(
            UUID courseId,
            String courseCode,
            String courseName,
            String skillPair,
            String status,
            int activeStudents,
            OffsetDateTime nextSessionAt,
            int pendingReviews,
            int revisionRequests,
            int completionPercent
    ) {}

    public record EvaluationQueueItem(
            String itemType,
            UUID itemId,
            UUID courseId,
            String courseCode,
            String courseName,
            UUID classActivityId,
            UUID sessionId,
            Integer sessionNo,
            String sessionTitle,
            String activityTitle,
            String skill,
            UUID studentId,
            String studentName,
            short attemptNo,
            OffsetDateTime submittedAt,
            boolean late,
            String submissionStatus,
            String reviewStatus,
            String courseRoute
    ) {}
}
