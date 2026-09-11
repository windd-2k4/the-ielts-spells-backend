package com.theieltsspells.academic.application;

import com.theieltsspells.academic.application.dto.CourseTeacherAssignmentResponse;
import com.theieltsspells.academic.domain.ClassTeacher;
import com.theieltsspells.academic.infrastructure.persistence.ClassTeacherRepository;
import com.theieltsspells.identity.application.StaffDirectoryService;
import com.theieltsspells.shared.persistence.enums.AppRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Maintains teachers who need course-scoped access, including substitutes. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseTeacherRosterService {

    private final ClassTeacherRepository assignments;
    private final StaffDirectoryService staffDirectory;

    public List<CourseTeacherAssignmentResponse> list(UUID courseId) {
        return assignments.findByCourseIdOrderByIsPrimaryDescAssignedAtAsc(courseId).stream()
                .map(this::response)
                .toList();
    }

    public Optional<UUID> primaryTeacherId(UUID courseId) {
        return assignments.findByCourseIdAndIsPrimaryTrue(courseId).map(ClassTeacher::getTeacherId);
    }

    /** Returns the requested teacher or the course primary teacher and ensures course access. */
    @Transactional
    public UUID resolveSessionTeacher(UUID courseId, UUID requestedTeacherId) {
        UUID teacherId = requestedTeacherId == null ? primaryTeacherId(courseId).orElse(null) : requestedTeacherId;
        if (teacherId == null) return null;
        staffDirectory.requireActiveTeacher(teacherId);
        ensureMember(courseId, teacherId, false);
        return teacherId;
    }

    @Transactional
    public ClassTeacher ensureMember(UUID courseId, UUID teacherId, boolean primary) {
        var assignment = assignments.findByCourseIdAndTeacherId(courseId, teacherId).orElseGet(() -> {
            var value = new ClassTeacher();
            value.setCourseId(courseId);
            value.setTeacherId(teacherId);
            value.setTeachingRole(AppRole.TEACHER);
            value.setAssignedAt(OffsetDateTime.now());
            value.setIsPrimary(false);
            return value;
        });
        if (primary) assignment.setIsPrimary(true);
        return assignments.save(assignment);
    }

    private CourseTeacherAssignmentResponse response(ClassTeacher value) {
        var profile = value.getTeacherRef() == null ? null : value.getTeacherRef().getUserRef();
        return new CourseTeacherAssignmentResponse(
                value.getTeacherId(),
                profile == null ? null : profile.getFullName(),
                profile == null ? null : profile.getEmail(),
                Boolean.TRUE.equals(value.getIsPrimary()),
                value.getAssignedAt());
    }
}

