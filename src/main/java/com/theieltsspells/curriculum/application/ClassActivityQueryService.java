package com.theieltsspells.curriculum.application;

import com.theieltsspells.curriculum.domain.ClassActivity;
import com.theieltsspells.curriculum.domain.ActivityChecklistItem;
import com.theieltsspells.curriculum.infrastructure.persistence.ActivityChecklistItemRepository;
import com.theieltsspells.curriculum.infrastructure.persistence.ClassActivityRepository;
import com.theieltsspells.shared.persistence.enums.SkillType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClassActivityQueryService {
    private final ClassActivityRepository activities;
    private final ActivityChecklistItemRepository checklistItems;

    public List<ClassActivity> publishedForCourse(UUID courseId) {
        return activities.findPublishedByCourseId(courseId);
    }

    public List<ClassActivity> publishedForCourse(UUID courseId, UUID sessionId, SkillType skill) {
        return activities.findPublishedByCourseIdAndSessionIdAndSkill(courseId, sessionId, skill);
    }

    public List<ActivityChecklistItem> checklistForActivities(Collection<UUID> activityIds) {
        return checklistItems.findByActivityIdInOrderByDisplayOrder(activityIds);
    }
}
