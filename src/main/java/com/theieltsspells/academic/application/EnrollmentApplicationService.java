package com.theieltsspells.academic.application;

import com.theieltsspells.academic.application.dto.*;
import com.theieltsspells.academic.domain.Enrollment;
import com.theieltsspells.academic.infrastructure.persistence.ClassRepository;
import com.theieltsspells.academic.infrastructure.persistence.EnrollmentRepository;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.persistence.enums.EnrollmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentApplicationService {
    private static final List<EnrollmentStatus> CAPACITY_STATUSES = List.of(EnrollmentStatus.PENDING, EnrollmentStatus.ACTIVE);
    private static final Map<EnrollmentStatus, Set<EnrollmentStatus>> TRANSITIONS = Map.of(
            EnrollmentStatus.PENDING, EnumSet.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.WITHDRAWN),
            EnrollmentStatus.ACTIVE, EnumSet.of(EnrollmentStatus.PAUSED, EnrollmentStatus.COMPLETED, EnrollmentStatus.WITHDRAWN),
            EnrollmentStatus.PAUSED, EnumSet.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.WITHDRAWN),
            EnrollmentStatus.COMPLETED, EnumSet.noneOf(EnrollmentStatus.class),
            EnrollmentStatus.WITHDRAWN, EnumSet.noneOf(EnrollmentStatus.class));

    private final EnrollmentRepository enrollments;
    private final ClassRepository classes;

    @Transactional
    public EnrollmentResponse enroll(EnrollStudentRequest request) {
        var targetClass = classes.findById(request.classId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học: " + request.classId()));
        if (enrollments.existsByClassIdAndStudentId(request.classId(), request.studentId()))
            throw new ConflictException("Học viên đã được ghi danh vào lớp này");
        var occupied = enrollments.countByClassIdAndStatusIn(request.classId(), CAPACITY_STATUSES);
        if (occupied >= targetClass.getCapacity()) throw new BusinessRuleException("Lớp học đã đủ số lượng học viên");

        var value = new Enrollment();
        value.setClassId(request.classId()); value.setStudentId(request.studentId());
        value.setStatus(EnrollmentStatus.PENDING); value.setNotes(request.notes());
        return AcademicMapper.toResponse(enrollments.save(value));
    }

    public EnrollmentResponse get(UUID id) { return AcademicMapper.toResponse(find(id)); }

    public Page<EnrollmentResponse> list(Pageable pageable) {
        return enrollments.findAll(pageable).map(AcademicMapper::toResponse);
    }

    public Page<EnrollmentResponse> listByClass(UUID classId, Pageable pageable) {
        return enrollments.findByClassId(classId, pageable).map(AcademicMapper::toResponse);
    }

    public Page<EnrollmentResponse> listByStudent(UUID studentId, Pageable pageable) {
        return enrollments.findByStudentId(studentId, pageable).map(AcademicMapper::toResponse);
    }

    @Transactional
    public EnrollmentResponse update(UUID id, UpdateEnrollmentRequest request) {
        var value = find(id);
        if (value.getStatus() != request.status()) {
            var allowed = TRANSITIONS.getOrDefault(value.getStatus(), Set.of());
            if (!allowed.contains(request.status()))
                throw new BusinessRuleException("Không thể chuyển trạng thái ghi danh từ " + value.getStatus() + " sang " + request.status());
            if (request.status() == EnrollmentStatus.ACTIVE && value.getStartedOn() == null) value.setStartedOn(LocalDate.now());
            if (request.status() == EnrollmentStatus.COMPLETED || request.status() == EnrollmentStatus.WITHDRAWN)
                value.setEndedOn(LocalDate.now());
            value.setStatus(request.status());
        }
        value.setNotes(request.notes());
        return AcademicMapper.toResponse(enrollments.save(value));
    }

    private Enrollment find(UUID id) {
        return enrollments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt ghi danh: " + id));
    }
}
