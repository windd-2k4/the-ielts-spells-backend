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
    private final ClassRepository classes;
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

    public List<ReservationResponse> reservations(UUID enrollmentId) {
        return reservations.findByEnrollmentIdOrderByRequestedAtDesc(enrollmentId).stream().map(this::response).toList();
    }

    @Transactional
    public TransferResponse requestTransfer(UUID enrollmentId, CreateTransferRequest request) {
        var source = enrollment(enrollmentId);
        if (source.getStatus() != EnrollmentStatus.ACTIVE && source.getStatus() != EnrollmentStatus.PAUSED)
            throw new BusinessRuleException("Chỉ lượt ghi danh đang học hoặc bảo lưu mới có thể chuyển lớp");
        var target = classes.findById(request.targetClassId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp đích: " + request.targetClassId()));
        if (source.getClassId().equals(target.getId())) throw new BusinessRuleException("Lớp đích phải khác lớp hiện tại");
        if (enrollments.existsByClassIdAndStudentId(target.getId(), source.getStudentId())) throw new ConflictException("Học viên đã có lượt ghi danh tại lớp đích");
        if (transfers.existsBySourceEnrollmentIdAndStatus(enrollmentId, "PENDING")) throw new ConflictException("Đã có yêu cầu chuyển lớp đang chờ duyệt");
        var value = new ClassTransfer();
        value.setSourceEnrollmentId(enrollmentId); value.setTargetClassId(target.getId()); value.setStatus("PENDING");
        value.setReason(request.reason()); value.setFeeAdjustment(request.feeAdjustment() == null ? BigDecimal.ZERO : request.feeAdjustment());
        value.setReservationId(request.reservationId()); value.setNotes(request.notes());
        return response(transfers.save(value));
    }

    @Transactional
    public TransferResponse approveTransfer(UUID id) {
        var value = transfers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu chuyển lớp: " + id));
        if (!"PENDING".equals(value.getStatus())) throw new BusinessRuleException("Yêu cầu chuyển lớp không còn ở trạng thái chờ duyệt");
        var source = enrollment(value.getSourceEnrollmentId());
        var target = classes.findById(value.getTargetClassId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp đích"));
        var occupied = enrollments.countByClassIdAndStatusIn(target.getId(), List.of(EnrollmentStatus.PENDING, EnrollmentStatus.ACTIVE));
        if (occupied >= target.getCapacity()) throw new BusinessRuleException("Lớp đích đã đủ sĩ số");

        var targetEnrollment = new Enrollment();
        targetEnrollment.setClassId(target.getId()); targetEnrollment.setStudentId(source.getStudentId());
        targetEnrollment.setStatus(EnrollmentStatus.ACTIVE); targetEnrollment.setStartedOn(LocalDate.now());
        targetEnrollment.setNotes("Chuyển từ lượt ghi danh " + source.getId());
        targetEnrollment = enrollments.save(targetEnrollment);

        source.setStatus(EnrollmentStatus.WITHDRAWN); source.setEndedOn(LocalDate.now());
        enrollments.save(source);
        value.setTargetEnrollmentId(targetEnrollment.getId()); value.setStatus("APPROVED"); value.setApprovedAt(OffsetDateTime.now());
        transfers.save(value);
        if (value.getReservationId() != null) reservations.findById(value.getReservationId()).ifPresent(reservation -> { reservation.setStatus("USED"); reservation.setTargetClassId(target.getId()); reservations.save(reservation); });
        return response(value);
    }

    public List<TransferResponse> transfers(UUID enrollmentId) {
        return transfers.findBySourceEnrollmentIdOrderByRequestedAtDesc(enrollmentId).stream().map(this::response).toList();
    }

    private Enrollment enrollment(UUID id) { return enrollments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt ghi danh: " + id)); }
    private ReservationResponse response(EnrollmentReservation v) { return new ReservationResponse(v.getId(), v.getEnrollmentId(), v.getStatus(), v.getReason(), v.getSessionsConsumed(), v.getSessionsRemaining(), v.getCreditAmount(), v.getExpiresOn(), v.getTargetClassId(), v.getRequestedAt(), v.getApprovedAt(), v.getNotes()); }
    private TransferResponse response(ClassTransfer v) { return new TransferResponse(v.getId(), v.getSourceEnrollmentId(), v.getTargetClassId(), v.getTargetEnrollmentId(), v.getReservationId(), v.getStatus(), v.getReason(), v.getFeeAdjustment(), v.getRequestedAt(), v.getApprovedAt(), v.getNotes()); }
}

