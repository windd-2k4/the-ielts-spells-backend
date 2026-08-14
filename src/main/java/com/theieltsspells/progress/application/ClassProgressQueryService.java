package com.theieltsspells.progress.application;

import com.theieltsspells.curriculum.application.ClassActivityQueryService;
import com.theieltsspells.progress.application.dto.*;
import com.theieltsspells.progress.domain.StudentActivityAttempt;
import com.theieltsspells.progress.infrastructure.persistence.StudentActivityAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassProgressQueryService {
    private final ClassActivityQueryService classActivities;
    private final StudentActivityAttemptRepository attempts;

    public List<ClassActivityProgressResponse> courseProgress(UUID courseId) {
        var activities = classActivities.publishedForCourse(courseId);
        if (activities.isEmpty()) return List.of();
        var ids = activities.stream().map(value -> value.getId()).toList();
        var byActivity = attempts.findByClassActivityIdInOrderByAttemptNoDesc(ids).stream().collect(java.util.stream.Collectors.groupingBy(StudentActivityAttempt::getClassActivityId));
        return activities.stream().map(item -> {
            var activity = item.getActivityRef();
            boolean required = item.getIsRequiredOverride() != null ? item.getIsRequiredOverride() : Boolean.TRUE.equals(activity.getIsRequired());
            return new ClassActivityProgressResponse(item.getId(), item.getSessionId(), item.getActivityId(), activity.getTitle(), activity.getSkill(), activity.getActivityType(), activity.getCompletionMethod(), item.getOpensAt(), item.getDueAt(), required, byActivity.getOrDefault(item.getId(), List.of()).stream().map(this::attempt).toList());
        }).toList();
    }

    private ActivityAttemptResponse attempt(StudentActivityAttempt v) { return new ActivityAttemptResponse(v.getId(), v.getStudentId(), v.getAttemptNo(), v.getSource(), v.getStatus(), v.getReviewStatus(), v.getScore(), v.getMaxScore(), v.getCorrectCount(), v.getIncorrectCount(), v.getUnansweredCount(), v.getDurationSeconds(), v.getComprehensionPercent(), v.getErrorAnalysis(), v.getImprovementPlan(), v.getSubmittedAt(), v.getCompletedAt(), Boolean.TRUE.equals(v.getIsLate())); }
}
