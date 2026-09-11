package com.theieltsspells.academic.application;

import com.theieltsspells.academic.application.dto.CourseResponse;
import com.theieltsspells.academic.application.dto.CourseStudentSupportAssignmentResponse;
import com.theieltsspells.academic.application.dto.ReplaceCourseStudentSupportsRequest;
import com.theieltsspells.academic.application.dto.StudentSupportStudentResponse;
import com.theieltsspells.academic.domain.CourseStudentSupport;
import com.theieltsspells.academic.infrastructure.persistence.CourseRepository;
import com.theieltsspells.academic.infrastructure.persistence.CourseStudentSupportRepository;
import com.theieltsspells.academic.infrastructure.persistence.EnrollmentRepository;
import com.theieltsspells.identity.application.StaffDirectoryService;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Maintains and queries the course-scoped Student Support roster. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseStudentSupportApplicationService {

    private final CourseRepository courses;
    private final CourseStudentSupportRepository assignments;
    private final StaffDirectoryService staffDirectory;
    private final EnrollmentRepository enrollments;

    public List<CourseStudentSupportAssignmentResponse> listForCourse(UUID courseId) {
        requireCourse(courseId);
        return assignments.findByCourseIdOrderByAssignedAtAsc(courseId).stream()
                .map(this::assignment)
                .toList();
    }

    @Transactional
    public List<CourseStudentSupportAssignmentResponse> replaceForCourse(
            UUID courseId,
            ReplaceCourseStudentSupportsRequest request,
            UUID assignedBy
    ) {
        requireCourse(courseId);
        Set<UUID> requestedIds = new LinkedHashSet<>(request.studentSupportIds());
        if (requestedIds.size() != request.studentSupportIds().size()) {
            throw new BusinessRuleException("Không được phân công một nhân sự hỗ trợ nhiều lần trong cùng khóa");
        }
        requestedIds.forEach(staffDirectory::requireActiveStudentSupport);

        List<CourseStudentSupport> existing = assignments.findByCourseIdOrderByAssignedAtAsc(courseId);
        Map<UUID, CourseStudentSupport> existingBySupportId = existing.stream()
                .collect(Collectors.toMap(CourseStudentSupport::getStudentSupportId, Function.identity()));

        assignments.deleteAll(existing.stream()
                .filter(value -> !requestedIds.contains(value.getStudentSupportId()))
                .toList());

        OffsetDateTime now = OffsetDateTime.now();
        requestedIds.stream()
                .filter(id -> !existingBySupportId.containsKey(id))
                .map(id -> newAssignment(courseId, id, assignedBy, now))
                .forEach(assignments::save);

        return assignments.findByCourseIdOrderByAssignedAtAsc(courseId).stream()
                .map(this::assignment)
                .toList();
    }

    public List<CourseResponse> assignedCourses(UUID studentSupportId) {
        return assignments.findByStudentSupportIdOrderByAssignedAtDesc(studentSupportId).stream()
                .map(CourseStudentSupport::getCourseId)
                .distinct()
                .flatMap(courseId -> courses.findById(courseId).stream())
                .map(AcademicMapper::toResponse)
                .toList();
    }

    public Page<StudentSupportStudentResponse> students(UUID courseId, Pageable pageable) {
        requireCourse(courseId);
        return enrollments.findByCourseId(courseId, pageable).map(enrollment -> {
            var student = enrollment.getStudentRef();
            var profile = student.getUserRef();
            return new StudentSupportStudentResponse(
                    student.getUserId(),
                    student.getStudentCode(),
                    profile.getFullName(),
                    profile.getEmail(),
                    profile.getPhone(),
                    profile.getAvatarPath(),
                    student.getCurrentBand(),
                    student.getTargetBand(),
                    enrollment.getStatus(),
                    enrollment.getStartedOn(),
                    enrollment.getPlannedExamMonth(),
                    enrollment.getExamRegistrationStatus());
        });
    }

    private CourseStudentSupport newAssignment(UUID courseId, UUID studentSupportId, UUID assignedBy, OffsetDateTime now) {
        var value = new CourseStudentSupport();
        value.setCourseId(courseId);
        value.setStudentSupportId(studentSupportId);
        value.setAssignedBy(assignedBy);
        value.setAssignedAt(now);
        return value;
    }

    private CourseStudentSupportAssignmentResponse assignment(CourseStudentSupport value) {
        return new CourseStudentSupportAssignmentResponse(
                value.getStudentSupportId(), value.getAssignedBy(), value.getAssignedAt());
    }

    private void requireCourse(UUID courseId) {
        if (!courses.existsById(courseId)) {
            throw new ResourceNotFoundException("Không tìm thấy khóa học: " + courseId);
        }
    }

}
