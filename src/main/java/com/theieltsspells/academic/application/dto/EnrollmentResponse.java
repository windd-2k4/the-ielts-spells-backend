package com.theieltsspells.academic.application.dto;

import com.theieltsspells.shared.persistence.enums.EnrollmentStatus;
import java.time.*;
import java.util.UUID;

public record EnrollmentResponse(
        UUID id, UUID courseId, UUID studentId, EnrollmentStatus status,
        OffsetDateTime enrolledAt, LocalDate startedOn, LocalDate endedOn, String notes,
        LocalDate plannedExamMonth, LocalDate actualExamDate,
        String examRegistrationStatus, String targetNote
) {}
