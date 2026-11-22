package com.theieltsspells.progress.api;

import com.theieltsspells.progress.application.ClassProgressQueryService;
import com.theieltsspells.progress.application.dto.CreateCourseActivityRequest;
import com.theieltsspells.shared.persistence.enums.SkillType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Public progress-module entry point for creating the activity paired with an assigned test. */
@Service
@RequiredArgsConstructor
public class CourseActivityCreationFacade {

    private final ClassProgressQueryService classProgress;

    public UUID create(UUID courseId, UUID sessionId, String title, SkillType skill,
                       OffsetDateTime dueAt, UUID actorId) {
        return classProgress.createActivity(courseId,
                new CreateCourseActivityRequest(sessionId, title, skill, dueAt), actorId);
    }
}
