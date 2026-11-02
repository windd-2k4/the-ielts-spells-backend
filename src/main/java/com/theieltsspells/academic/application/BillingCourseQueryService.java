package com.theieltsspells.academic.application;

import com.theieltsspells.academic.domain.Course;
import com.theieltsspells.academic.infrastructure.persistence.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingCourseQueryService {
    private final CourseRepository courses;

    public Optional<BillingCourseView> findById(UUID courseId) {
        return courses.findById(courseId).map(this::toView);
    }

    public Map<UUID, BillingCourseView> findByIds(Set<UUID> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Map.of();
        }
        return courses.findAllById(courseIds).stream()
                .map(this::toView)
                .collect(Collectors.toMap(BillingCourseView::id, Function.identity()));
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
