package com.theieltsspells.academic.application;

import com.theieltsspells.academic.application.dto.*;
import com.theieltsspells.academic.domain.Course;
import com.theieltsspells.academic.infrastructure.persistence.CourseRepository;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseApplicationService {
    private final CourseRepository courses;

    @Transactional
    public CourseResponse create(CreateCourseRequest request) {
        var code = normalizeCode(request.code());
        if (courses.existsByCodeIgnoreCase(code)) throw new ConflictException("Mã khóa học đã tồn tại: " + code);
        var course = new Course();
        course.setCode(code);
        apply(course, request.name(), request.description(), request.level(), request.targetBand(),
                request.totalSessions(), request.tuitionAmount(), request.isPublic() == null ? false : request.isPublic(), true);
        course.setCreatedBy(request.createdBy());
        return AcademicMapper.toResponse(courses.save(course));
    }

    public CourseResponse get(UUID id) { return AcademicMapper.toResponse(find(id)); }

    public Page<CourseResponse> list(Boolean active, Pageable pageable) {
        return courses.findByIsActive(active == null ? true : active, pageable).map(AcademicMapper::toResponse);
    }

    @Transactional
    public CourseResponse update(UUID id, UpdateCourseRequest request) {
        var course = find(id);
        apply(course, request.name(), request.description(), request.level(), request.targetBand(),
                request.totalSessions(), request.tuitionAmount(), request.isPublic(), request.isActive());
        course.setUpdatedAt(OffsetDateTime.now());
        return AcademicMapper.toResponse(courses.save(course));
    }

    @Transactional
    public void deactivate(UUID id) {
        var course = find(id);
        course.setIsActive(false);
        course.setUpdatedAt(OffsetDateTime.now());
    }

    private Course find(UUID id) {
        return courses.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khóa học: " + id));
    }

    private void apply(Course c, String name, String description, String level, java.math.BigDecimal targetBand,
                       Short sessions, java.math.BigDecimal tuition, Boolean isPublic, Boolean isActive) {
        c.setName(name.trim()); c.setDescription(description); c.setLevel(level);
        c.setTargetBand(targetBand); c.setTotalSessions(sessions); c.setTuitionAmount(tuition);
        c.setIsPublic(isPublic); c.setIsActive(isActive);
    }

    private String normalizeCode(String code) { return code.trim().toUpperCase(Locale.ROOT); }
}
