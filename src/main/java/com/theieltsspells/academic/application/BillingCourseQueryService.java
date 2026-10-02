package com.theieltsspells.academic.application;

import com.theieltsspells.academic.domain.Course;
import com.theieltsspells.academic.infrastructure.persistence.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingCourseQueryService {
    private final CourseRepository courses;

    public Optional<BillingCourseView> findById(UUID courseId) {
        return courses.findById(courseId).map(this::toView);
    }

    public List<BillingCourseView> findActive() {
        return courses.findAll().stream()
                .map(this::toView)
                .filter(BillingCourseView::active)
                .toList();
    }

    private BillingCourseView toView(Course course) {
        return new BillingCourseView(
                course.getId(),
                course.getCode(),
                course.getName(),
                course.getTuitionAmount(),
                course.getLevel(),
                Boolean.TRUE.equals(course.getIsActive())
        );
    }
}
