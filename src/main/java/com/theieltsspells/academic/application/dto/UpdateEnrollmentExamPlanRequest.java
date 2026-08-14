package com.theieltsspells.academic.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateEnrollmentExamPlanRequest(
        LocalDate plannedExamMonth,
        LocalDate actualExamDate,
        @NotBlank
        @Pattern(regexp = "NOT_REGISTERED|REGISTERED|ISSUE")
        String examRegistrationStatus,
        @Size(max = 255) String targetNote
) {}
