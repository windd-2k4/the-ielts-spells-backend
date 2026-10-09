package com.theieltsspells.academic.application;

import com.theieltsspells.academic.application.dto.CourseSessionItemRequest;
import com.theieltsspells.academic.application.dto.UpsertClassSessionRequest;
import com.theieltsspells.academic.domain.ClassSession;
import com.theieltsspells.academic.domain.CourseSessionItem;
import com.theieltsspells.academic.infrastructure.persistence.ClassSessionRepository;
import com.theieltsspells.academic.infrastructure.persistence.CourseRepository;
import com.theieltsspells.academic.infrastructure.persistence.CourseSessionItemRepository;
import com.theieltsspells.shared.persistence.enums.SessionStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClassSessionApplicationServiceTests {

    @Test
    void sessionAssignmentCanReferencePublishedTestBankEntry() {
        ClassSessionRepository sessions = mock(ClassSessionRepository.class);
        CourseSessionItemRepository items = mock(CourseSessionItemRepository.class);
        CourseRepository courses = mock(CourseRepository.class);
        CourseTeacherRosterService teachers = mock(CourseTeacherRosterService.class);
        ClassSessionApplicationService service = new ClassSessionApplicationService(sessions, items, courses, teachers);
        UUID courseId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        OffsetDateTime startsAt = OffsetDateTime.now();

        when(courses.existsById(courseId)).thenReturn(true);
        when(sessions.save(any())).thenAnswer(invocation -> {
            ClassSession value = invocation.getArgument(0);
            value.setId(sessionId);
            return value;
        });
        when(items.findBySessionIdOrderByDisplayOrderAscCreatedAtAsc(sessionId)).thenReturn(List.of());

        service.create(courseId, new UpsertClassSessionRequest(
                (short) 1, "Session 1", startsAt, startsAt.plusHours(2), null, null,
                SessionStatus.SCHEDULED, null, null, null, null,
                List.of(new CourseSessionItemRequest(
                        "ASSIGNMENT", "Reading passage 1", null, null, testId,
                        null, null, startsAt.plusDays(2), true, "STUDENT"))));

        ArgumentCaptor<CourseSessionItem> saved = ArgumentCaptor.forClass(CourseSessionItem.class);
        verify(items).save(saved.capture());
        assertThat(saved.getValue().getItemType()).isEqualTo("ASSIGNMENT");
        assertThat(saved.getValue().getSourceTestId()).isEqualTo(testId);
    }
}
