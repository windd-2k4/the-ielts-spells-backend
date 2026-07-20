package com.theieltsspells.academic.application;

import com.theieltsspells.academic.application.dto.*;
import com.theieltsspells.academic.infrastructure.persistence.ClassRepository;
import com.theieltsspells.academic.infrastructure.persistence.CourseRepository;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.persistence.enums.ClassStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassApplicationService {
    private final ClassRepository classes;
    private final CourseRepository courses;

    @Transactional
    public ClassResponse create(CreateClassRequest request) {
        var course = courses.findById(request.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khóa học: " + request.courseId()));
        if (!Boolean.TRUE.equals(course.getIsActive())) throw new BusinessRuleException("Không thể mở lớp cho khóa học đã ngừng hoạt động");
        validateDates(request.startsOn(), request.endsOn());
        var code = request.code().trim().toUpperCase(Locale.ROOT);
        if (classes.existsByCodeIgnoreCase(code)) throw new ConflictException("Mã lớp đã tồn tại: " + code);

        var value = new com.theieltsspells.academic.domain.Class();
        value.setCourseId(request.courseId()); value.setCode(code); value.setName(request.name().trim());
        value.setCapacity(request.capacity()); value.setStartsOn(request.startsOn()); value.setEndsOn(request.endsOn());
        value.setStatus(request.status() == null ? ClassStatus.PLANNED : request.status());
        value.setDefaultZoomUrl(request.defaultZoomUrl()); value.setCreatedBy(request.createdBy());
        return AcademicMapper.toResponse(classes.save(value));
    }

    public ClassResponse get(UUID id) { return AcademicMapper.toResponse(find(id)); }

    public Page<ClassResponse> list(Pageable pageable) {
        return classes.findAll(pageable).map(AcademicMapper::toResponse);
    }

    public Page<ClassResponse> listByCourse(UUID courseId, Pageable pageable) {
        return classes.findByCourseId(courseId, pageable).map(AcademicMapper::toResponse);
    }

    public Page<ClassResponse> listByStatus(ClassStatus status, Pageable pageable) {
        return classes.findByStatus(status, pageable).map(AcademicMapper::toResponse);
    }

    public List<ClassResponse> upcoming(LocalDate from, LocalDate to) {
        validateDates(from, to);
        return classes.findByStartsOnBetweenOrderByStartsOnAsc(from, to).stream().map(AcademicMapper::toResponse).toList();
    }

    @Transactional
    public ClassResponse update(UUID id, UpdateClassRequest request) {
        validateDates(request.startsOn(), request.endsOn());
        var value = find(id);
        value.setName(request.name().trim()); value.setCapacity(request.capacity());
        value.setStartsOn(request.startsOn()); value.setEndsOn(request.endsOn()); value.setStatus(request.status());
        value.setDefaultZoomUrl(request.defaultZoomUrl()); value.setUpdatedAt(OffsetDateTime.now());
        return AcademicMapper.toResponse(classes.save(value));
    }

    private com.theieltsspells.academic.domain.Class find(UUID id) {
        return classes.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học: " + id));
    }

    private void validateDates(LocalDate startsOn, LocalDate endsOn) {
        if (endsOn != null && endsOn.isBefore(startsOn))
            throw new BusinessRuleException("Ngày kết thúc không được trước ngày bắt đầu");
    }
}
