package com.theieltsspells.progress.application;

import com.theieltsspells.curriculum.application.ClassActivityQueryService;
import com.theieltsspells.curriculum.domain.ClassActivity;
import com.theieltsspells.curriculum.domain.ActivityChecklistItem;
import com.theieltsspells.curriculum.infrastructure.persistence.ActivityChecklistItemRepository;
import com.theieltsspells.progress.application.dto.ActivityAttemptResponse;
import com.theieltsspells.progress.application.dto.ActivityChecklistItemResponse;
import com.theieltsspells.progress.application.dto.AttemptChecklistResultResponse;
import com.theieltsspells.progress.application.dto.ActivityEvidenceResponse;
import com.theieltsspells.progress.application.dto.ClassActivityProgressResponse;
import com.theieltsspells.progress.domain.ActivityEvidence;
import com.theieltsspells.progress.domain.AttemptChecklistResult;
import com.theieltsspells.progress.domain.StudentActivityAttempt;
import com.theieltsspells.progress.infrastructure.persistence.ActivityEvidenceRepository;
import com.theieltsspells.progress.infrastructure.persistence.AttemptChecklistResultRepository;
import com.theieltsspells.progress.infrastructure.persistence.StudentActivityAttemptRepository;
import com.theieltsspells.shared.persistence.enums.SkillType;
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
    private final ActivityChecklistItemRepository checklistItems;
    private final AttemptChecklistResultRepository checklistResults;
    private final ActivityEvidenceRepository evidence;

    public List<ClassActivityProgressResponse> courseProgress(UUID courseId) {
        return courseProgress(courseId, null, null);
    }

    public List<ClassActivityProgressResponse> courseProgress(UUID courseId, UUID sessionId, SkillType skill) {
        var activities = sessionId == null || skill == null
                ? classActivities.publishedForCourse(courseId)
                : classActivities.publishedForCourse(courseId, sessionId, skill);
        if (activities.isEmpty()) {
            return List.of();
        }

        var activityIds = activities.stream().map(ClassActivity::getId).toList();
        Map<UUID, List<StudentActivityAttempt>> attemptsByActivity = attempts
                .findByClassActivityIdInOrderByAttemptNoDesc(activityIds)
                .stream()
                .collect(Collectors.groupingBy(StudentActivityAttempt::getClassActivityId));
        var learningActivityIds = activities.stream().map(ClassActivity::getActivityId).toList();
        Map<UUID, List<ActivityChecklistItem>> checklistByActivity = checklistItems
                .findByActivityIdInOrderByDisplayOrder(learningActivityIds).stream()
                .collect(Collectors.groupingBy(ActivityChecklistItem::getActivityId));
        var allAttempts = attemptsByActivity.values().stream().flatMap(List::stream).toList();
        var attemptIds = allAttempts.stream().map(StudentActivityAttempt::getId).toList();
        Map<UUID, List<AttemptChecklistResult>> checklistByAttempt = attemptIds.isEmpty() ? Map.of()
                : checklistResults.findByAttemptIdIn(attemptIds).stream()
                .collect(Collectors.groupingBy(AttemptChecklistResult::getAttemptId));
        Map<UUID, List<ActivityEvidence>> evidenceByAttempt = attemptIds.isEmpty() ? Map.of()
                : evidence.findByAttemptIdIn(attemptIds).stream()
                .collect(Collectors.groupingBy(ActivityEvidence::getAttemptId));

        return activities.stream()
                .map(activity -> response(
                        activity,
                        checklistByActivity.getOrDefault(activity.getActivityId(), List.of()),
                        attemptsByActivity.getOrDefault(activity.getId(), List.of()),
                        checklistByAttempt,
                        evidenceByAttempt))
                .toList();
    }

    private ClassActivityProgressResponse response(
            ClassActivity classActivity,
            List<ActivityChecklistItem> activityChecklist,
            List<StudentActivityAttempt> activityAttempts,
            Map<UUID, List<AttemptChecklistResult>> checklistByAttempt,
            Map<UUID, List<ActivityEvidence>> evidenceByAttempt
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
                activityChecklist.stream().map(item -> new ActivityChecklistItemResponse(
                        item.getId(), item.getLabel(), item.getDescription(),
                        item.getDisplayOrder(), Boolean.TRUE.equals(item.getIsRequired()))).toList(),
                activityAttempts.stream().map(value -> attempt(
                        value,
                        checklistByAttempt.getOrDefault(value.getId(), List.of()),
                        evidenceByAttempt.getOrDefault(value.getId(), List.of()))).toList());
    }

    private ActivityAttemptResponse attempt(StudentActivityAttempt attempt,
            List<AttemptChecklistResult> results, List<ActivityEvidence> attemptEvidence) {
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
                Boolean.TRUE.equals(attempt.getIsLate()),
                results.stream().map(result -> new AttemptChecklistResultResponse(
                        result.getChecklistItemId(), Boolean.TRUE.equals(result.getIsCompleted()),
                        result.getCompletedAt(), result.getNote())).toList(),
                attemptEvidence.stream().map(item -> new ActivityEvidenceResponse(
                        item.getId(), item.getEvidenceType(), item.getOriginalName(), item.getUploadedAt())).toList());
    }
}
