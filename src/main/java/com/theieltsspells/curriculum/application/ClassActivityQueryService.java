package com.theieltsspells.curriculum.application;

import com.theieltsspells.curriculum.domain.ClassActivity;
import com.theieltsspells.curriculum.infrastructure.persistence.ClassActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClassActivityQueryService {
    private final ClassActivityRepository activities;

    public List<ClassActivity> publishedForCourse(UUID courseId) {
        return activities.findPublishedByCourseId(courseId);
    }
}
