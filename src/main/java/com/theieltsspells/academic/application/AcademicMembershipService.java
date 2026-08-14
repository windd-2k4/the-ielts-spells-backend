package com.theieltsspells.academic.application;

import com.theieltsspells.academic.domain.ClassSession;
import com.theieltsspells.shared.persistence.enums.EnrollmentStatus;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.academic.infrastructure.persistence.ClassSessionRepository;
import com.theieltsspells.academic.infrastructure.persistence.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class AcademicMembershipService {
    private final ClassSessionRepository sessions;
    private final EnrollmentRepository enrollments;

    public List<UUID> sessionIds(UUID courseId) {
        return sessions.findByCourseIdOrderBySessionNo(courseId).stream().map(ClassSession::getId).toList();
    }

    public List<ClassSession> sessions(UUID courseId) {
        return sessions.findByCourseIdOrderBySessionNo(courseId);
    }

    public Optional<ClassSession> session(UUID courseId, UUID sessionId) {
        return sessions.findByIdAndCourseId(sessionId, courseId);
    }

    public boolean isEnrolled(UUID courseId, UUID studentId) {
        return enrollments.existsByCourseIdAndStudentId(courseId, studentId);
    }

    public List<AttendanceRosterMember> attendanceRoster(UUID courseId, UUID sessionId) {
        session(courseId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Buổi học không thuộc khóa học đã chọn"));
        return enrollments.findByCourseId(courseId).stream()
                // Attendance mirrors the current course roster. Pending learners and
                // learners enrolled after this session must still be shown as unmarked.
                // Only a withdrawn enrollment is no longer part of the course roster.
                .filter(value -> value.getStatus() != EnrollmentStatus.WITHDRAWN)
                .map(value -> new AttendanceRosterMember(
                        value.getStudentId(),
                        value.getStudentRef().getStudentCode(),
                        value.getStudentRef().getUserRef().getFullName(),
                        value.getStudentRef().getUserRef().getEmail(),
                        value.getStudentRef().getUserRef().getAvatarPath()))
                .sorted(Comparator.comparing(AttendanceRosterMember::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

}
