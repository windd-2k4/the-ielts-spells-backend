package com.theieltsspells.progress.application;

import com.theieltsspells.curriculum.application.ClassActivityQueryService;
import com.theieltsspells.curriculum.domain.ClassActivity;
import com.theieltsspells.progress.application.dto.ActivityAttemptResponse;
import com.theieltsspells.progress.application.dto.ClassActivityProgressResponse;
import com.theieltsspells.progress.domain.StudentActivityAttempt;
import com.theieltsspells.progress.infrastructure.persistence.StudentActivityAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassProgressQueryService {
    private final ClassActivityQueryService classActivities;
    private final StudentActivityAttemptRepository attempts;

    public List<ClassActivityProgressResponse> courseProgress(UUID courseId) {
        var activities = classActivities.publishedForCourse(courseId);
        if (activities.isEmpty()) {
            return List.of();
        }

        var activityIds = activities.stream().map(ClassActivity::getId).toList();
        Map<UUID, List<StudentActivityAttempt>> attemptsByActivity = attempts
                .findByClassActivityIdInOrderByAttemptNoDesc(activityIds)
                .stream()
                .collect(Collectors.groupingBy(StudentActivityAttempt::getClassActivityId));

        return activities.stream()
                .map(activity -> response(activity, attemptsByActivity.getOrDefault(activity.getId(), List.of())))
                .toList();
    }

    private ClassActivityProgressResponse response(
            ClassActivity classActivity,
            List<StudentActivityAttempt> activityAttempts
    ) {
        var activity = classActivity.getActivityRef();
        boolean required = classActivity.getIsRequiredOverride() != null
                ? classActivity.getIsRequiredOverride()
                : Boolean.TRUE.equals(activity.getIsRequired());
        return new ClassActivityProgressResponse(
                classActivity.getId(),
                classActivity.getSessionId(),
                classActivity.getActivityId(),
                activity.getTitle(),
                activity.getSkill(),
                activity.getActivityType(),
                activity.getCompletionMethod(),
                classActivity.getOpensAt(),
                classActivity.getDueAt(),
                required,
                activityAttempts.stream().map(this::attempt).toList());
    }

    private ActivityAttemptResponse attempt(StudentActivityAttempt attempt) {
        return new ActivityAttemptResponse(
                attempt.getId(),
                attempt.getStudentId(),
                attempt.getAttemptNo(),
                attempt.getSource(),
                attempt.getStatus(),
                attempt.getReviewStatus(),
                attempt.getScore(),
                attempt.getMaxScore(),
                attempt.getCorrectCount(),
                attempt.getIncorrectCount(),
                attempt.getUnansweredCount(),
                attempt.getDurationSeconds(),
                attempt.getComprehensionPercent(),
                attempt.getErrorAnalysis(),
                attempt.getImprovementPlan(),
                attempt.getSubmittedAt(),
                attempt.getCompletedAt(),
                Boolean.TRUE.equals(attempt.getIsLate()));
    }
}
