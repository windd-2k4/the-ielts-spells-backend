package com.theieltsspells.academic.application;

import com.theieltsspells.academic.application.dto.CourseTeacherAssignmentResponse;
import com.theieltsspells.academic.application.dto.SetPrimaryCourseTeacherRequest;
import com.theieltsspells.academic.infrastructure.persistence.ClassSessionRepository;
import com.theieltsspells.academic.infrastructure.persistence.ClassTeacherRepository;
import com.theieltsspells.academic.infrastructure.persistence.CourseRepository;
import com.theieltsspells.identity.application.StaffDirectoryService;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.persistence.enums.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseTeacherApplicationService {

    private final CourseRepository courses;
    private final ClassTeacherRepository assignments;
    private final ClassSessionRepository sessions;
    private final CourseTeacherRosterService roster;
    private final StaffDirectoryService staffDirectory;

    public List<CourseTeacherAssignmentResponse> listForCourse(UUID courseId) {
        requireCourse(courseId);
        return roster.list(courseId);
    }

    @Transactional
    public List<CourseTeacherAssignmentResponse> setPrimary(
            UUID courseId,
            SetPrimaryCourseTeacherRequest request
    ) {
        requireCourse(courseId);
        UUID teacherId = request.teacherId();
        staffDirectory.requireActiveTeacher(teacherId);

        var currentPrimary = assignments.findByCourseIdAndIsPrimaryTrue(courseId).orElse(null);
        UUID previousTeacherId = currentPrimary == null ? null : currentPrimary.getTeacherId();
        if (Objects.equals(previousTeacherId, teacherId)) return roster.list(courseId);

        if (currentPrimary != null) {
            currentPrimary.setIsPrimary(false);
            assignments.saveAndFlush(currentPrimary);
        }
        roster.ensureMember(courseId, teacherId, true);

        sessions.findByCourseIdOrderBySessionNo(courseId).stream()
                .filter(session -> session.getStatus() == SessionStatus.SCHEDULED)
                .filter(session -> session.getTeacherId() == null
                        || Objects.equals(session.getTeacherId(), previousTeacherId))
                .forEach(session -> session.setTeacherId(teacherId));

        return roster.list(courseId);
    }

    private void requireCourse(UUID courseId) {
        if (!courses.existsById(courseId)) {
            throw new ResourceNotFoundException("Không tìm thấy khóa học: " + courseId);
        }
    }
}
