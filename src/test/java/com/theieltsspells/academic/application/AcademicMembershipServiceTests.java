package com.theieltsspells.academic.application;

import com.theieltsspells.academic.domain.ClassSession;
import com.theieltsspells.academic.domain.Enrollment;
import com.theieltsspells.academic.infrastructure.persistence.ClassSessionRepository;
import com.theieltsspells.academic.infrastructure.persistence.EnrollmentRepository;
import com.theieltsspells.identity.domain.Profile;
import com.theieltsspells.identity.domain.StudentProfile;
import com.theieltsspells.shared.persistence.enums.EnrollmentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicMembershipServiceTests {

    @Mock
    private ClassSessionRepository sessions;

    @Mock
    private EnrollmentRepository enrollments;

    @InjectMocks
    private AcademicMembershipService service;

    @Test
    void attendanceRosterIncludesLearnerWhoEnrolledAfterSession() {
        var courseId = UUID.randomUUID();
        var sessionId = UUID.randomUUID();
        when(sessions.findByIdAndCourseId(sessionId, courseId)).thenReturn(Optional.of(mock(ClassSession.class)));

        var lateEnrollment = enrollment(EnrollmentStatus.ACTIVE, "HV002", "Lam Thuy");
        lateEnrollment.setStartedOn(LocalDate.of(2026, 8, 12));
        when(enrollments.findByCourseId(courseId)).thenReturn(List.of(lateEnrollment));

        var roster = service.attendanceRoster(courseId, sessionId);

        assertThat(roster).extracting(AttendanceRosterMember::studentCode).containsExactly("HV002");
    }

    @Test
    void attendanceRosterIncludesPendingButExcludesWithdrawnEnrollments() {
        var courseId = UUID.randomUUID();
        var sessionId = UUID.randomUUID();
        when(sessions.findByIdAndCourseId(sessionId, courseId)).thenReturn(Optional.of(mock(ClassSession.class)));
        when(enrollments.findByCourseId(courseId)).thenReturn(List.of(
                enrollment(EnrollmentStatus.ACTIVE, "HV001", "Khiem Roki"),
                enrollment(EnrollmentStatus.PENDING, "HV002", "Lam Thuy"),
                enrollment(EnrollmentStatus.WITHDRAWN, "HV003", "Huynh Oanh")
        ));

        var roster = service.attendanceRoster(courseId, sessionId);

        assertThat(roster).extracting(AttendanceRosterMember::studentCode)
                .containsExactly("HV001", "HV002");
    }

    private Enrollment enrollment(EnrollmentStatus status, String studentCode, String fullName) {
        var profile = new Profile();
        profile.setFullName(fullName);
        profile.setEmail(studentCode.toLowerCase() + "@example.test");
        var student = new StudentProfile();
        student.setUserId(UUID.randomUUID());
        student.setStudentCode(studentCode);
        student.setUserRef(profile);
        var enrollment = new Enrollment();
        enrollment.setStatus(status);
        enrollment.setStudentId(student.getUserId());
        enrollment.setStudentRef(student);
        return enrollment;
    }
}
