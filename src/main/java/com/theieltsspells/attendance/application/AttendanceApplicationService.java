package com.theieltsspells.attendance.application;

import com.theieltsspells.academic.application.AcademicMembershipService;
import com.theieltsspells.academic.domain.ClassSession;
import com.theieltsspells.attendance.application.dto.*;
import com.theieltsspells.attendance.domain.AttendanceRecord;
import com.theieltsspells.attendance.domain.AttendanceSheet;
import com.theieltsspells.attendance.domain.AttendanceSheetStatus;
import com.theieltsspells.attendance.infrastructure.persistence.AttendanceRecordRepository;
import com.theieltsspells.attendance.infrastructure.persistence.AttendanceSheetRepository;
import com.theieltsspells.attendance.infrastructure.persistence.AttendanceSheetAuditRepository;
import com.theieltsspells.attendance.domain.AttendanceSheetAudit;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.persistence.enums.AttendanceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceApplicationService {
    private final AttendanceRecordRepository records;
    private final AttendanceSheetRepository sheets;
    private final AttendanceSheetAuditRepository audits;
    private final AcademicMembershipService academic;

    @Transactional
    public List<AttendanceSessionSummaryResponse> listSessions(UUID courseId) {
        var sessions = academic.sessions(courseId);
        if (sessions.isEmpty()) return List.of();
        var sessionIds = sessions.stream().map(ClassSession::getId).toList();
        var sheetBySession = sheets.findBySessionIdIn(sessionIds).stream()
                .collect(Collectors.toMap(AttendanceSheet::getSessionId, Function.identity()));
        sessions.stream()
                .filter(session -> sheetBySession.containsKey(session.getId()))
                .forEach(session -> synchronizeRoster(courseId, session.getId()));
        var recordsBySession = records.findBySessionIdInOrderBySessionId(sessionIds).stream()
                .collect(Collectors.groupingBy(AttendanceRecord::getSessionId));
        return sessions.stream()
                .map(session -> summary(session, sheetBySession.get(session.getId()),
                        recordsBySession.getOrDefault(session.getId(), List.of())))
                .toList();
    }

    @Transactional
    public AttendanceSheetResponse getSheet(UUID courseId, UUID sessionId) {
        var session = requireSession(courseId, sessionId);
        var sheet = sheets.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Buổi học chưa khởi tạo bảng điểm danh"));
        synchronizeRoster(courseId, sessionId);
        return sheetResponse(session, sheet);
    }

    @Transactional
    public AttendanceSheetResponse initialize(UUID courseId, UUID sessionId, UUID actorId) {
        var session = requireSession(courseId, sessionId);
        boolean isNew = sheets.findBySessionId(sessionId).isEmpty();
        var sheet = sheets.findBySessionId(sessionId).orElseGet(() -> {
            var value = new AttendanceSheet();
            value.setSessionId(sessionId);
            value.setStatus(AttendanceSheetStatus.DRAFT);
            value.setPreparedBy(actorId);
            return sheets.save(value);
        });
        if (isNew) audit(sheet, "INITIALIZED", actorId, null);
        if (sheet.getStatus() == AttendanceSheetStatus.DRAFT) synchronizeRoster(courseId, sessionId);
        return sheetResponse(session, sheet);
    }

    @Transactional
    public AttendanceSheetResponse saveDraft(UUID courseId, UUID sessionId,
                                             BulkUpdateAttendanceRequest request, UUID actorId) {
        var session = requireSession(courseId, sessionId);
        var sheet = requireEditableSheet(sessionId);
        synchronizeRoster(courseId, sessionId);
        var byStudent = records.findRosterBySessionId(sessionId).stream()
                .collect(Collectors.toMap(AttendanceRecord::getStudentId, Function.identity()));
        for (var entry : request.entries()) {
            var record = Optional.ofNullable(byStudent.get(entry.studentId()))
                    .orElseThrow(() -> new BusinessRuleException("Học viên không có trong danh sách điểm danh của buổi học"));
            apply(record, entry, actorId);
            records.save(record);
        }
        audit(sheet, "DRAFT_SAVED", actorId, request.entries().size() + " dòng được lưu");
        return sheetResponse(session, sheet);
    }

    @Transactional
    public AttendanceSheetResponse lock(UUID courseId, UUID sessionId, UUID actorId) {
        var session = requireSession(courseId, sessionId);
        var sheet = requireEditableSheet(sessionId);
        synchronizeRoster(courseId, sessionId);
        var values = records.findRosterBySessionId(sessionId);
        if (values.isEmpty()) throw new BusinessRuleException("Buổi học chưa có học viên để điểm danh");
        long pending = values.stream().filter(value -> value.getStatus() == AttendanceStatus.PENDING).count();
        if (pending > 0) throw new BusinessRuleException("Còn " + pending + " học viên chưa được đánh dấu");
        var now = OffsetDateTime.now();
        values.forEach(value -> {
            value.setIsConfirmed(true);
            value.setConfirmedBy(actorId);
            value.setConfirmedAt(now);
        });
        records.saveAll(values);
        sheet.setStatus(AttendanceSheetStatus.LOCKED);
        sheet.setLockedBy(actorId);
        sheet.setLockedAt(now);
        sheets.save(sheet);
        audit(sheet, "LOCKED", actorId, null);
        return sheetResponse(session, sheet);
    }

    @Transactional
    public AttendanceSheetResponse reopen(UUID courseId, UUID sessionId, UUID actorId, String reason) {
        var session = requireSession(courseId, sessionId);
        var sheet = requireSheet(sessionId);
        if (sheet.getStatus() != AttendanceSheetStatus.LOCKED)
            throw new BusinessRuleException("Bảng điểm danh chưa bị khóa");
        var now = OffsetDateTime.now();
        sheet.setStatus(AttendanceSheetStatus.DRAFT);
        sheet.setReopenedBy(actorId);
        sheet.setReopenedAt(now);
        sheet.setReopenReason(reason.trim());
        var values = records.findRosterBySessionId(sessionId);
        values.forEach(value -> value.setIsConfirmed(false));
        records.saveAll(values);
        sheets.save(sheet);
        audit(sheet, "REOPENED", actorId, reason.trim());
        return sheetResponse(session, sheet);
    }

    public List<AttendanceResponse> list(UUID courseId) {
        var ids = academic.sessionIds(courseId);
        if (ids.isEmpty()) return List.of();
        return records.findBySessionIdInOrderBySessionId(ids).stream().map(this::legacyResponse).toList();
    }

    @Transactional
    public AttendanceResponse upsert(UUID courseId, UpsertAttendanceRequest request, UUID actorId) {
        var session = requireSession(courseId, request.sessionId());
        var sheet = requireEditableSheet(session.getId());
        if (!academic.isEnrolled(courseId, request.studentId()))
            throw new BusinessRuleException("Học viên chưa được ghi danh vào khóa học này");
        var value = records.findBySessionIdAndStudentId(session.getId(), request.studentId())
                .orElseGet(AttendanceRecord::new);
        value.setSessionId(session.getId());
        value.setStudentId(request.studentId());
        apply(value, new AttendanceEntryRequest(request.studentId(), request.status(), request.joinedAt(),
                request.leftAt(), request.durationSeconds(), request.adjustmentReason()), actorId);
        records.save(value);
        return legacyResponse(value);
    }

    private void synchronizeRoster(UUID courseId, UUID sessionId) {
        var existing = records.findRosterBySessionId(sessionId).stream()
                .map(AttendanceRecord::getStudentId).collect(Collectors.toSet());
        var missing = academic.attendanceRoster(courseId, sessionId).stream()
                .filter(student -> !existing.contains(student.studentId()))
                .map(student -> {
                    var value = new AttendanceRecord();
                    value.setSessionId(sessionId);
                    value.setStudentId(student.studentId());
                    value.setStatus(AttendanceStatus.PENDING);
                    value.setMatchMethod("manual");
                    value.setIsConfirmed(false);
                    return value;
                }).toList();
        if (!missing.isEmpty()) records.saveAll(missing);
    }

    private void apply(AttendanceRecord value, AttendanceEntryRequest entry, UUID actorId) {
        if (entry.durationSeconds() != null && entry.durationSeconds() < 0)
            throw new BusinessRuleException("Thời lượng tham dự không hợp lệ");
        if (entry.joinedAt() != null && entry.leftAt() != null && !entry.leftAt().isAfter(entry.joinedAt()))
            throw new BusinessRuleException("Thời gian rời lớp phải sau thời gian vào lớp");
        Integer duration = entry.durationSeconds();
        if (duration == null && entry.joinedAt() != null && entry.leftAt() != null)
            duration = Math.toIntExact(Duration.between(entry.joinedAt(), entry.leftAt()).toSeconds());
        value.setStatus(entry.status());
        value.setJoinedAt(entry.joinedAt());
        value.setLeftAt(entry.leftAt());
        value.setDurationSeconds(duration);
        value.setAdjustmentReason(blank(entry.note()));
        value.setUpdatedBy(actorId);
        value.setMatchMethod("manual");
        value.setIsConfirmed(false);
        value.setConfirmedBy(null);
        value.setConfirmedAt(null);
    }

    private AttendanceSheetResponse sheetResponse(ClassSession session, AttendanceSheet sheet) {
        var values = records.findRosterBySessionId(session.getId());
        return new AttendanceSheetResponse(summary(session, sheet, values), values.stream().map(value -> {
            var student = value.getStudentRef();
            var profile = student.getUserRef();
            return new AttendanceStudentRowResponse(value.getId(), value.getStudentId(), student.getStudentCode(),
                    profile.getFullName(), profile.getEmail(), profile.getAvatarPath(), value.getStatus(),
                    value.getJoinedAt(), value.getLeftAt(), value.getDurationSeconds(),
                    "zoom".equalsIgnoreCase(value.getMatchMethod()) ? "ZOOM" : "MANUAL",
                    value.getAdjustmentReason());
        }).toList());
    }

    private AttendanceSessionSummaryResponse summary(ClassSession session, AttendanceSheet sheet,
                                                     List<AttendanceRecord> values) {
        int present = count(values, AttendanceStatus.PRESENT);
        int late = count(values, AttendanceStatus.LATE);
        int leftEarly = count(values, AttendanceStatus.LEFT_EARLY);
        int absent = count(values, AttendanceStatus.ABSENT);
        int excused = count(values, AttendanceStatus.EXCUSED);
        int pending = count(values, AttendanceStatus.PENDING);
        int denominator = present + late + leftEarly + absent;
        var rate = denominator == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(present + late + leftEarly)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
        return new AttendanceSessionSummaryResponse(session.getId(), session.getSessionNo(), session.getTitle(),
                session.getStartsAt(), session.getEndsAt(),
                session.getTeacherRef() == null ? null : session.getTeacherRef().getFullName(),
                sheet == null ? null : sheet.getStatus(), values.size(), values.size() - pending,
                present, late, leftEarly, absent, excused, pending, rate,
                sheet == null ? null : sheet.getLockedAt());
    }

    private int count(List<AttendanceRecord> values, AttendanceStatus status) {
        return (int) values.stream().filter(value -> value.getStatus() == status).count();
    }

    private ClassSession requireSession(UUID courseId, UUID sessionId) {
        return academic.session(courseId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Buổi học không thuộc khóa học đã chọn"));
    }

    private AttendanceSheet requireSheet(UUID sessionId) {
        return sheets.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Buổi học chưa khởi tạo bảng điểm danh"));
    }

    private AttendanceSheet requireEditableSheet(UUID sessionId) {
        var sheet = requireSheet(sessionId);
        if (sheet.getStatus() == AttendanceSheetStatus.LOCKED)
            throw new BusinessRuleException("Bảng điểm danh đã khóa. Quản lý cần mở lại trước khi chỉnh sửa");
        return sheet;
    }

    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private void audit(AttendanceSheet sheet, String action, UUID actorId, String reason) {
        var value = new AttendanceSheetAudit();
        value.setSheetId(sheet.getId());
        value.setAction(action);
        value.setActorId(actorId);
        value.setReason(reason);
        audits.save(value);
    }

    private AttendanceResponse legacyResponse(AttendanceRecord value) {
        return new AttendanceResponse(value.getId(), value.getSessionId(), value.getStudentId(), value.getStatus(),
                value.getJoinedAt(), value.getLeftAt(), value.getDurationSeconds(),
                Boolean.TRUE.equals(value.getIsConfirmed()), value.getAdjustmentReason());
    }
}
