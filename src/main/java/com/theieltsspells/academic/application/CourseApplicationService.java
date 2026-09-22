package com.theieltsspells.academic.application;

import com.theieltsspells.academic.application.dto.*;
import com.theieltsspells.academic.domain.Course;
import com.theieltsspells.academic.infrastructure.persistence.*;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.persistence.enums.ClassStatus;
import com.theieltsspells.shared.persistence.enums.EnrollmentStatus;
import com.theieltsspells.shared.persistence.enums.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseApplicationService {
    private final CourseRepository courses;
    private final CourseCodeGenerator codeGenerator;
    private final EnrollmentRepository enrollments;
    private final ClassSessionRepository classSessions;
    private final ClassTeacherRepository classTeachers;
    private final CourseStudentSupportRepository courseStudentSupports;

    @Transactional
    public CourseResponse create(CreateCourseRequest request) {
        var code = generateUniqueCode(request);
        var course = new Course();
        course.setCode(code);
        course.setProgramId(request.programId());
        apply(course, request.name(), request.description(), request.level(), request.skillPair(), request.targetBand(),
                request.totalSessions(), request.tuitionAmount(), request.capacity(), request.startsOn(), request.endsOn(),
                normalizeRequestedStatus(request.status()), request.defaultZoomUrl(),
                request.isPublic() == null ? false : request.isPublic(), true);
        course.setCreatedBy(request.createdBy());
        return AcademicMapper.toResponse(courses.save(course));
    }

    @Transactional
    public CourseResponse get(UUID id) {
        return AcademicMapper.toResponse(synchronizeLifecycle(find(id)));
    }

    @Transactional
    public Page<CourseResponse> list(Boolean active, Pageable pageable) {
        var page = courses.findByIsActive(active == null ? true : active, pageable);
        synchronizeLifecycle(page.getContent());
        return page.map(AcademicMapper::toResponse);
    }

    @Transactional
    public Page<CourseResponse> listByStatus(ClassStatus status, Pageable pageable) {
        synchronizeLifecycle(courses.findByIsActive(true, Pageable.unpaged()).getContent());
        var effectiveStatus = normalizeRequestedStatus(status);
        return courses.findByStatusAndIsActiveTrue(effectiveStatus, pageable).map(AcademicMapper::toResponse);
    }

    @Transactional
    public List<CourseResponse> upcoming(LocalDate from, LocalDate to) {
        validateDates(from, to);
        var values = courses.findByStartsOnBetweenAndIsActiveTrueOrderByStartsOnAsc(from, to);
        synchronizeLifecycle(values);
        return values.stream()
                .map(AcademicMapper::toResponse).toList();
    }

    @Transactional
    public CourseResponse update(UUID id, UpdateCourseRequest request) {
        var course = find(id);
        long activeEnrollments = enrollments.countByCourseIdAndStatusIn(id, List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.PENDING));
        if (request.capacity() < activeEnrollments) {
            throw new BusinessRuleException("Sĩ số tối đa (" + request.capacity() + ") không thể nhỏ hơn số học viên hiện tại (" + activeEnrollments + " học viên)");
        }
        apply(course, request.name(), request.description(), request.level(), request.skillPair(), request.targetBand(),
                request.totalSessions(), request.tuitionAmount(), request.capacity(), request.startsOn(), request.endsOn(),
                normalizeRequestedStatus(request.status()), request.defaultZoomUrl(), request.isPublic(), request.isActive());
        synchronizeLifecycle(course);
        course.setUpdatedAt(OffsetDateTime.now());
        return AcademicMapper.toResponse(courses.save(course));
    }

    @Transactional
    public void deactivate(UUID id) {
        var course = find(id);
        course.setIsActive(false);
        course.setUpdatedAt(OffsetDateTime.now());
        courses.save(course);
    }

    @Transactional
    public CourseResponse restore(UUID id) {
        var course = find(id);
        course.setIsActive(true);
        synchronizeLifecycle(course);
        course.setUpdatedAt(OffsetDateTime.now());
        return AcademicMapper.toResponse(courses.save(course));
    }

    @Transactional
    public void deletePermanently(UUID id) {
        var course = find(id);
        long totalEnrollments = enrollments.countByCourseId(id);
        if (totalEnrollments > 0) {
            throw new BusinessRuleException("Không thể xóa vĩnh viễn khóa học đã có " + totalEnrollments + " học viên ghi danh. Vui lòng chọn ngừng hoạt động.");
        }
        var sessions = classSessions.findByCourseIdOrderBySessionNo(id);
        boolean hasCompletedSessions = sessions.stream().anyMatch(s -> s.getStatus() == SessionStatus.COMPLETED);
        if (hasCompletedSessions) {
            throw new BusinessRuleException("Không thể xóa vĩnh viễn khóa học đã có buổi học diễn ra.");
        }
        classSessions.deleteAll(sessions);
        classTeachers.deleteAll(classTeachers.findByCourseIdOrderByIsPrimaryDescAssignedAtAsc(id));
        courseStudentSupports.deleteAll(courseStudentSupports.findByCourseIdOrderByAssignedAtAsc(id));
        courses.delete(course);
    }

    private Course find(UUID id) {
        return courses.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khóa học: " + id));
    }

    private void apply(Course c, String name, String description, String level,
                       com.theieltsspells.shared.persistence.enums.SkillPair skillPair,
                       java.math.BigDecimal targetBand, Short sessions, java.math.BigDecimal tuition,
                       Short capacity, LocalDate startsOn, LocalDate endsOn, ClassStatus status,
                       String defaultZoomUrl, Boolean isPublic, Boolean isActive) {
        validateDates(startsOn, endsOn);
        c.setName(name.trim()); c.setDescription(description); c.setLevel(level);
        c.setSkillPair(skillPair);
        c.setTargetBand(targetBand); c.setTotalSessions(sessions); c.setTuitionAmount(tuition);
        c.setCapacity(capacity); c.setStartsOn(startsOn); c.setEndsOn(endsOn); c.setStatus(status);
        c.setDefaultZoomUrl(defaultZoomUrl);
        c.setIsPublic(isPublic); c.setIsActive(isActive);
    }

    private void validateDates(LocalDate startsOn, LocalDate endsOn) {
        if (endsOn != null && endsOn.isBefore(startsOn)) {
            throw new BusinessRuleException("Ngày kết thúc không được trước ngày bắt đầu");
        }
    }

    private ClassStatus normalizeRequestedStatus(ClassStatus status) {
        return status == null || status == ClassStatus.PLANNED ? ClassStatus.OPEN : status;
    }

    private Course synchronizeLifecycle(Course course) {
        var current = course.getStatus();
        if (current == ClassStatus.PLANNED) {
            current = ClassStatus.OPEN;
        }
        if (current == ClassStatus.OPEN
                && course.getStartsOn() != null
                && !course.getStartsOn().isAfter(LocalDate.now())) {
            current = ClassStatus.ACTIVE;
        }
        if (course.getStatus() != current) {
            course.setStatus(current);
            course.setUpdatedAt(OffsetDateTime.now());
            return courses.save(course);
        }
        return course;
    }

    private void synchronizeLifecycle(List<Course> values) {
        values.forEach(this::synchronizeLifecycle);
    }

    private String generateUniqueCode(CreateCourseRequest request) {
        for (var attempt = 0; attempt < 10; attempt++) {
            var candidate = codeGenerator.next(request.skillPair(), request.startsOn());
            if (!courses.existsByCodeIgnoreCase(candidate)) return candidate;
        }
        throw new ConflictException("Không thể cấp mã khóa học duy nhất. Vui lòng thử lại.");
    }
}
