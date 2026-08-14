package com.theieltsspells.academic.application;

import com.theieltsspells.academic.application.dto.*;
import com.theieltsspells.academic.domain.Course;
import com.theieltsspells.academic.domain.Enrollment;

final class AcademicMapper {
    private AcademicMapper() {}

    static CourseResponse toResponse(Course value) {
        return new CourseResponse(value.getId(), value.getProgramId(), value.getCode(), value.getName(), value.getDescription(),
                value.getLevel(), value.getSkillPair(), value.getTargetBand(), value.getTotalSessions(), value.getTuitionAmount(),
                value.getCapacity(), value.getStartsOn(), value.getEndsOn(), value.getStatus(), value.getDefaultZoomUrl(),
                value.getIsPublic(), value.getIsActive(), value.getCreatedBy(), value.getCreatedAt(), value.getUpdatedAt());
    }

    static EnrollmentResponse toResponse(Enrollment value) {
        return new EnrollmentResponse(value.getId(), value.getCourseId(), value.getStudentId(), value.getStatus(),
                value.getEnrolledAt(), value.getStartedOn(), value.getEndedOn(), value.getNotes(),
                value.getPlannedExamMonth(), value.getActualExamDate(),
                value.getExamRegistrationStatus(), value.getTargetNote());
    }
}
