package com.theieltsspells.studentportal.application.dto;

import java.time.LocalDate;

public record StudentExamPlanResponse(
        boolean noExamPlan,
        LocalDate plannedExamDate,
        boolean learningSetupComplete
) {
}
