package com.theieltsspells.academic.application;

import com.theieltsspells.academic.application.dto.*;
import com.theieltsspells.academic.domain.*;
import com.theieltsspells.academic.infrastructure.persistence.*;
import com.theieltsspells.shared.application.*;
import com.theieltsspells.shared.persistence.enums.EnrollmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentLifecycleService {
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    private final EnrollmentReservationRepository reservations;
    private final ClassTransferRepository transfers;

    @Transactional
    public ReservationResponse requestReservation(UUID enrollmentId, CreateReservationRequest request) {
        var enrollment = enrollment(enrollmentId);
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE)
            throw new BusinessRuleException("Chỉ lượt ghi danh đang học mới có thể bảo lưu");
        if (reservations.existsByEnrollmentIdAndStatus(enrollmentId, "PENDING"))
            throw new ConflictException("Lượt ghi danh đã có yêu cầu bảo lưu đang chờ duyệt");
        var value = new EnrollmentReservation();
        value.setEnrollmentId(enrollmentId); value.setStatus("PENDING"); value.setReason(request.reason());
        value.setSessionsConsumed(request.sessionsConsumed()); value.setSessionsRemaining(request.sessionsRemaining());
        value.setCreditAmount(request.creditAmount()); value.setExpiresOn(request.expiresOn()); value.setNotes(request.notes());
        return response(reservations.save(value));
    }

    @Transactional
    public ReservationResponse approveReservation(UUID id) {
        var value = reservations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bảo lưu: " + id));
        if (!"PENDING".equals(value.getStatus())) throw new BusinessRuleException("Yêu cầu bảo lưu không còn ở trạng thái chờ duyệt");
        var enrollment = enrollment(value.getEnrollmentId());
        enrollment.setStatus(EnrollmentStatus.PAUSED);
        value.setStatus("APPROVED"); value.setApprovedAt(OffsetDateTime.now());
        enrollments.save(enrollment); reservations.save(value);
        return response(value);
    }

    @Transactional
    public ReservationResponse rejectReservation(UUID id, String reason) {
        var value = reservations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bảo lưu: " + id));
        if (!"PENDING".equals(value.getStatus())) throw new BusinessRuleException("Yêu cầu bảo lưu không còn chờ duyệt");
        value.setStatus("REJECTED"); value.setApprovedAt(OffsetDateTime.now());
        if (reason != null && !reason.isBlank()) value.setNotes(reason.trim());
        return response(reservations.save(value));
    }

    public List<ReservationResponse> reservations(UUID enrollmentId) {
        return reservations.findByEnrollmentIdOrderByRequestedAtDesc(enrollmentId).stream().map(this::response).toList();
    }

    @Transactional
    public TransferResponse requestTransfer(UUID enrollmentId, CreateTransferRequest request) {
        var source = enrollment(enrollmentId);
        if (source.getStatus() != EnrollmentStatus.ACTIVE && source.getStatus() != EnrollmentStatus.PAUSED)
            throw new BusinessRuleException("Chỉ lượt ghi danh đang học hoặc bảo lưu mới có thể chuyển lớp");
        var target = courses.findById(request.targetCourseId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khóa học đích: " + request.targetCourseId()));
        if (target.getStatus() == com.theieltsspells.shared.persistence.enums.ClassStatus.CANCELLED || target.getStatus() == com.theieltsspells.shared.persistence.enums.ClassStatus.COMPLETED)
            throw new BusinessRuleException("Không thể chuyển vào lớp đã kết thúc hoặc đã hủy");
        if (source.getCourseId().equals(target.getId())) throw new BusinessRuleException("Khóa học đích phải khác khóa học hiện tại");
        if (enrollments.existsByCourseIdAndStudentId(target.getId(), source.getStudentId())) throw new ConflictException("Học viên đã có lượt ghi danh tại khóa học đích");
        if (transfers.existsBySourceEnrollmentIdAndStatus(enrollmentId, "PENDING")) throw new ConflictException("Đã có yêu cầu chuyển lớp đang chờ duyệt");
        var value = new ClassTransfer();
        value.setSourceEnrollmentId(enrollmentId); value.setTargetCourseId(target.getId()); value.setStatus("PENDING");
        value.setReason(request.reason()); value.setFeeAdjustment(request.feeAdjustment() == null ? BigDecimal.ZERO : request.feeAdjustment());
        value.setReservationId(request.reservationId()); value.setNotes(request.notes());
        return response(transfers.save(value));
    }

    @Transactional
    public TransferResponse approveTransfer(UUID id) {
        var value = transfers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu chuyển lớp: " + id));
        if (!"PENDING".equals(value.getStatus())) throw new BusinessRuleException("Yêu cầu chuyển lớp không còn ở trạng thái chờ duyệt");
        var source = enrollment(value.getSourceEnrollmentId());
        var target = courses.findById(value.getTargetCourseId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khóa học đích"));
        if (enrollments.existsByCourseIdAndStudentId(target.getId(), source.getStudentId())) throw new ConflictException("Học viên đã có lượt ghi danh tại khóa học đích");
        var occupied = enrollments.countByCourseIdAndStatusIn(target.getId(), List.of(EnrollmentStatus.PENDING, EnrollmentStatus.ACTIVE));
        if (occupied >= target.getCapacity()) throw new BusinessRuleException("Lớp đích đã đủ sĩ số");

        var targetEnrollment = new Enrollment();
        targetEnrollment.setCourseId(target.getId()); targetEnrollment.setStudentId(source.getStudentId());
        targetEnrollment.setStatus(EnrollmentStatus.ACTIVE); targetEnrollment.setStartedOn(LocalDate.now());
        targetEnrollment.setNotes("Chuyển từ lượt ghi danh " + source.getId());
        targetEnrollment = enrollments.save(targetEnrollment);

        source.setStatus(EnrollmentStatus.WITHDRAWN); source.setEndedOn(LocalDate.now());
        enrollments.save(source);
        value.setTargetEnrollmentId(targetEnrollment.getId()); value.setStatus("APPROVED"); value.setApprovedAt(OffsetDateTime.now());
        transfers.save(value);
        if (value.getReservationId() != null) reservations.findById(value.getReservationId()).ifPresent(reservation -> { reservation.setStatus("USED"); reservation.setTargetCourseId(target.getId()); reservations.save(reservation); });
        return response(value);
    }

    @Transactional
    public TransferResponse rejectTransfer(UUID id, String reason) {
        var value = transfers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu chuyển lớp: " + id));
        if (!"PENDING".equals(value.getStatus())) throw new BusinessRuleException("Yêu cầu chuyển lớp không còn chờ duyệt");
        value.setStatus("REJECTED"); value.setApprovedAt(OffsetDateTime.now());
        if (reason != null && !reason.isBlank()) value.setNotes(reason.trim());
        return response(transfers.save(value));
    }

    public List<TransferResponse> transfers(UUID enrollmentId) {
        return transfers.findBySourceEnrollmentIdOrderByRequestedAtDesc(enrollmentId).stream().map(this::response).toList();
    }

    private Enrollment enrollment(UUID id) { return enrollments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt ghi danh: " + id)); }
    private ReservationResponse response(EnrollmentReservation v) { return new ReservationResponse(v.getId(), v.getEnrollmentId(), v.getStatus(), v.getReason(), v.getSessionsConsumed(), v.getSessionsRemaining(), v.getCreditAmount(), v.getExpiresOn(), v.getTargetCourseId(), v.getRequestedAt(), v.getApprovedAt(), v.getNotes()); }
    private TransferResponse response(ClassTransfer v) { return new TransferResponse(v.getId(), v.getSourceEnrollmentId(), v.getTargetCourseId(), v.getTargetEnrollmentId(), v.getReservationId(), v.getStatus(), v.getReason(), v.getFeeAdjustment(), v.getRequestedAt(), v.getApprovedAt(), v.getNotes()); }
}
