package com.theieltsspells.academic.application;

import com.theieltsspells.academic.application.dto.UpdateEnrollmentNoteRequest;
import com.theieltsspells.academic.domain.Enrollment;
import com.theieltsspells.academic.infrastructure.persistence.CourseRepository;
import com.theieltsspells.academic.infrastructure.persistence.EnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentApplicationServiceTests {

    @Mock
    private EnrollmentRepository enrollments;

    @Mock
    private CourseRepository courses;

    @InjectMocks
    private EnrollmentApplicationService service;

    @Test
    void updateNoteStoresOneCourseWideNote() {
        var courseId = UUID.randomUUID();
        var studentId = UUID.randomUUID();
        var enrollment = new Enrollment();
        enrollment.setCourseId(courseId);
        enrollment.setStudentId(studentId);
        when(enrollments.findByCourseIdAndStudentId(courseId, studentId)).thenReturn(Optional.of(enrollment));
        when(enrollments.save(enrollment)).thenReturn(enrollment);

        service.updateNote(courseId, studentId, new UpdateEnrollmentNoteRequest("  Chỉ học trực tiếp thứ Hai  "));

        assertThat(enrollment.getAttendanceNote()).isEqualTo("Chỉ học trực tiếp thứ Hai");
    }
}
