package com.theieltsspells.academic.application.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record UpdateCourseRequest(
        @NotBlank @Size(max = 200) String name,
        String description,
        @Size(max = 100) String level,
        @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal targetBand,
        @Positive Short totalSessions,
        @PositiveOrZero BigDecimal tuitionAmount,
        @NotNull Boolean isPublic,
        @NotNull Boolean isActive
) {}
