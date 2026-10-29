package com.theieltsspells.academic.application.dto;

import com.theieltsspells.shared.persistence.enums.EnrollmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Course-scoped student directory data safe for assigned teachers. */
public record CourseStudentResponse(
        UUID id,
        String studentCode,
        String fullName,
        String email,
        String phone,
        String avatarPath,
        BigDecimal currentBand,
        BigDecimal targetBand,
        boolean active,
        EnrollmentStatus lifecycleStatus,
        UUID currentCourseId,
        String currentCourseCode,
        String currentCourseName,
        long enrollmentCount,
        LocalDate joinedAt
) {}
