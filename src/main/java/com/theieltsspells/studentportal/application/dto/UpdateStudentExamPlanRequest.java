package com.theieltsspells.studentportal.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UpdateStudentExamPlanRequest(
        @NotNull Boolean noExamPlan,
        LocalDate plannedExamDate
) {
}
