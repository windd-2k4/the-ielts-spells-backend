package com.theieltsspells.academic.application;

import com.theieltsspells.academic.application.dto.*;
import com.theieltsspells.academic.domain.Course;
import com.theieltsspells.academic.domain.Enrollment;

final class AcademicMapper {
    private AcademicMapper() {}

    static CourseResponse toResponse(Course value) {
        return new CourseResponse(value.getId(), value.getCode(), value.getName(), value.getDescription(),
                value.getLevel(), value.getTargetBand(), value.getTotalSessions(), value.getTuitionAmount(),
                value.getIsPublic(), value.getIsActive(), value.getCreatedBy(), value.getCreatedAt(), value.getUpdatedAt());
    }

    static ClassResponse toResponse(com.theieltsspells.academic.domain.Class value) {
        return new ClassResponse(value.getId(), value.getCourseId(), value.getCode(), value.getName(),
                value.getCapacity(), value.getStartsOn(), value.getEndsOn(), value.getStatus(),
                value.getDefaultZoomUrl(), value.getCreatedBy(), value.getCreatedAt(), value.getUpdatedAt());
    }

    static EnrollmentResponse toResponse(Enrollment value) {
        return new EnrollmentResponse(value.getId(), value.getClassId(), value.getStudentId(), value.getStatus(),
                value.getEnrolledAt(), value.getStartedOn(), value.getEndedOn(), value.getNotes());
    }
}
