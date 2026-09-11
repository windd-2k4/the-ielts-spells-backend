package com.theieltsspells.academic.application;

import com.theieltsspells.academic.infrastructure.persistence.ClassTeacherRepository;
import com.theieltsspells.identity.application.StaffDirectoryService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseTeacherRosterServiceTests {

    @Test
    void selectedSubstituteBecomesACourseMember() {
        ClassTeacherRepository assignments = mock(ClassTeacherRepository.class);
        StaffDirectoryService staffDirectory = mock(StaffDirectoryService.class);
        CourseTeacherRosterService service = new CourseTeacherRosterService(assignments, staffDirectory);
        UUID courseId = UUID.randomUUID();
        UUID substituteId = UUID.randomUUID();

        when(assignments.findByCourseIdAndTeacherId(courseId, substituteId)).thenReturn(Optional.empty());
        when(assignments.save(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> call.getArgument(0));

        UUID resolved = service.resolveSessionTeacher(courseId, substituteId);

        assertThat(resolved).isEqualTo(substituteId);
        verify(staffDirectory).requireActiveTeacher(substituteId);
        verify(assignments).save(org.mockito.ArgumentMatchers.argThat(value ->
                value.getCourseId().equals(courseId)
                        && value.getTeacherId().equals(substituteId)
                        && !Boolean.TRUE.equals(value.getIsPrimary())));
    }
}
