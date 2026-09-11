package com.theieltsspells.academic.application.dto;

import com.theieltsspells.shared.persistence.enums.EnrollmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Minimum learner contact and study context needed by assigned Student Support
 * staff. Sensitive profile fields such as address and emergency contacts are
 * intentionally not included.
 */
public record StudentSupportStudentResponse(
        UUID studentId,
        String studentCode,
        String fullName,
        String email,
        String phone,
        String avatarPath,
        BigDecimal currentBand,
        BigDecimal targetBand,
        EnrollmentStatus enrollmentStatus,
        LocalDate startedOn,
        LocalDate plannedExamMonth,
        String examRegistrationStatus
) {
}
