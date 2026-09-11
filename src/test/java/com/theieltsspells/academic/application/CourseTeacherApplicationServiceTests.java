package com.theieltsspells.academic.application;

import com.theieltsspells.academic.application.dto.SetPrimaryCourseTeacherRequest;
import com.theieltsspells.academic.domain.ClassSession;
import com.theieltsspells.academic.domain.ClassTeacher;
import com.theieltsspells.academic.infrastructure.persistence.ClassSessionRepository;
import com.theieltsspells.academic.infrastructure.persistence.ClassTeacherRepository;
import com.theieltsspells.academic.infrastructure.persistence.CourseRepository;
import com.theieltsspells.identity.application.StaffDirectoryService;
import com.theieltsspells.shared.persistence.enums.SessionStatus;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseTeacherApplicationServiceTests {

    @Test
    void replacingPrimaryUpdatesOnlyScheduledSessionsUsingThePreviousPrimary() {
        CourseRepository courses = mock(CourseRepository.class);
        ClassTeacherRepository assignments = mock(ClassTeacherRepository.class);
        ClassSessionRepository sessions = mock(ClassSessionRepository.class);
        CourseTeacherRosterService roster = mock(CourseTeacherRosterService.class);
        StaffDirectoryService staffDirectory = mock(StaffDirectoryService.class);
        CourseTeacherApplicationService service = new CourseTeacherApplicationService(
                courses, assignments, sessions, roster, staffDirectory);

        UUID courseId = UUID.randomUUID();
        UUID previousId = UUID.randomUUID();
        UUID nextId = UUID.randomUUID();
        UUID substituteId = UUID.randomUUID();

        ClassTeacher previous = new ClassTeacher();
        previous.setCourseId(courseId);
        previous.setTeacherId(previousId);
        previous.setIsPrimary(true);

        ClassSession inherited = session(courseId, previousId, SessionStatus.SCHEDULED);
        ClassSession unassigned = session(courseId, null, SessionStatus.SCHEDULED);
        ClassSession substitute = session(courseId, substituteId, SessionStatus.SCHEDULED);
        ClassSession completed = session(courseId, previousId, SessionStatus.COMPLETED);

        when(courses.existsById(courseId)).thenReturn(true);
        when(assignments.findByCourseIdAndIsPrimaryTrue(courseId)).thenReturn(Optional.of(previous));
        when(sessions.findByCourseIdOrderBySessionNo(courseId))
                .thenReturn(List.of(inherited, unassigned, substitute, completed));
        when(roster.list(courseId)).thenReturn(List.of());

        service.setPrimary(courseId, new SetPrimaryCourseTeacherRequest(nextId));

        InOrder order = inOrder(staffDirectory, assignments, roster);
        order.verify(staffDirectory).requireActiveTeacher(nextId);
        order.verify(assignments).saveAndFlush(previous);
        order.verify(roster).ensureMember(courseId, nextId, true);
        assertThat(previous.getIsPrimary()).isFalse();
        assertThat(inherited.getTeacherId()).isEqualTo(nextId);
        assertThat(unassigned.getTeacherId()).isEqualTo(nextId);
        assertThat(substitute.getTeacherId()).isEqualTo(substituteId);
        assertThat(completed.getTeacherId()).isEqualTo(previousId);
    }

    private ClassSession session(UUID courseId, UUID teacherId, SessionStatus status) {
        ClassSession value = new ClassSession();
        value.setCourseId(courseId);
        value.setTeacherId(teacherId);
        value.setStatus(status);
        return value;
    }
}

