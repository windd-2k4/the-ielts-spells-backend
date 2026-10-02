package com.theieltsspells.academic.application;

import com.theieltsspells.academic.application.dto.*;
import com.theieltsspells.academic.domain.Enrollment;
import com.theieltsspells.academic.infrastructure.persistence.CourseRepository;
import com.theieltsspells.academic.infrastructure.persistence.EnrollmentRepository;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.persistence.enums.EnrollmentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Slf4j
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
    private final CourseRepository courses;

    @Transactional
    public EnrollmentResponse enroll(EnrollStudentRequest request) {
        var targetCourse = courses.findById(request.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khóa học: " + request.courseId()));
        if (!Boolean.TRUE.equals(targetCourse.getIsActive())
                || targetCourse.getStatus() == com.theieltsspells.shared.persistence.enums.ClassStatus.COMPLETED
                || targetCourse.getStatus() == com.theieltsspells.shared.persistence.enums.ClassStatus.CANCELLED) {
            throw new BusinessRuleException("Khóa học không còn nhận ghi danh");
        }
        if (enrollments.existsByCourseIdAndStudentId(request.courseId(), request.studentId())) {
            throw new ConflictException("Học viên đã được ghi danh vào khóa học này");
        }
        var occupied = enrollments.countByCourseIdAndStatusIn(request.courseId(), CAPACITY_STATUSES);
        if (occupied >= targetCourse.getCapacity()) {
            throw new BusinessRuleException("Khóa học đã đủ số lượng học viên");
        }

        var value = new Enrollment();
        value.setCourseId(request.courseId());
        value.setStudentId(request.studentId());
        value.setStatus(EnrollmentStatus.PENDING);
        value.setNotes(request.notes());
        return AcademicMapper.toResponse(enrollments.save(value));
    }

    /**
     * Ghi danh an toàn cho quy trình thanh toán/kích hoạt:
     * Chạy trong transaction riêng biệt (REQUIRES_NEW) để nếu học viên đã được ghi danh hoặc
     * có lỗi nghiệp vụ thì không gây đánh dấu rollback cho toàn bộ transaction thanh toán (SePay/Order).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<EnrollmentResponse> enrollSafely(EnrollStudentRequest request) {
        try {
            if (enrollments.existsByCourseIdAndStudentId(request.courseId(), request.studentId())) {
                log.info("Học viên {} đã được ghi danh vào khóa học {} từ trước. Bỏ qua ghi danh trùng.", request.studentId(), request.courseId());
                return Optional.empty();
            }
            return Optional.of(enroll(request));
        } catch (Exception ex) {
            log.warn("Lỗi khi tự động ghi danh học viên {}: {}", request.studentId(), ex.getMessage());
            return Optional.empty();
        }
    }

    public EnrollmentResponse get(UUID id) { return AcademicMapper.toResponse(find(id)); }

    public Page<EnrollmentResponse> list(Pageable pageable) {
        return enrollments.findAll(pageable).map(AcademicMapper::toResponse);
    }

    public Page<EnrollmentResponse> listByCourse(UUID courseId, Pageable pageable) {
        return enrollments.findByCourseId(courseId, pageable).map(AcademicMapper::toResponse);
    }

    public Page<EnrollmentResponse> listByStudent(UUID studentId, Pageable pageable) {
        return enrollments.findByStudentId(studentId, pageable).map(AcademicMapper::toResponse);
    }

    @Transactional
    public EnrollmentResponse update(UUID id, UpdateEnrollmentRequest request) {
        var value = find(id);
        if (value.getStatus() != request.status()) {
            var allowed = TRANSITIONS.getOrDefault(value.getStatus(), Set.of());
            if (!allowed.contains(request.status())) {
                throw new BusinessRuleException("Không thể chuyển trạng thái ghi danh từ " + value.getStatus() + " sang " + request.status());
            }
            if (request.status() == EnrollmentStatus.ACTIVE && value.getStartedOn() == null) value.setStartedOn(LocalDate.now());
            if (request.status() == EnrollmentStatus.COMPLETED || request.status() == EnrollmentStatus.WITHDRAWN) {
                value.setEndedOn(LocalDate.now());
            }
            value.setStatus(request.status());
        }
        value.setNotes(request.notes());
        return AcademicMapper.toResponse(enrollments.save(value));
    }

    @Transactional
    public EnrollmentResponse updateExamPlan(UUID id, UpdateEnrollmentExamPlanRequest request) {
        var value = find(id);
        if (request.actualExamDate() != null && "NOT_REGISTERED".equals(request.examRegistrationStatus())) {
            throw new BusinessRuleException("Không thể nhập ngày thi thực tế khi học viên chưa đăng ký thi");
        }
        value.setPlannedExamMonth(request.plannedExamMonth() == null
                ? null : request.plannedExamMonth().withDayOfMonth(1));
        value.setActualExamDate(request.actualExamDate());
        value.setExamRegistrationStatus(request.examRegistrationStatus());
        value.setTargetNote(request.targetNote() == null || request.targetNote().isBlank()
                ? null : request.targetNote().trim());
        return AcademicMapper.toResponse(enrollments.save(value));
    }

    public boolean isStudentEnrolledActive(UUID courseId, UUID studentId) {
        if (courseId == null || studentId == null) return false;
        return enrollments.existsByCourseIdAndStudentIdAndStatus(courseId, studentId, EnrollmentStatus.ACTIVE);
    }

    private Enrollment find(UUID id) {
        return enrollments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt ghi danh: " + id));
    }
}
