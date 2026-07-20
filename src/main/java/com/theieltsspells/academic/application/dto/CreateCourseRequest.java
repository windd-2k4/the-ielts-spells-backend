package com.theieltsspells.academic.application.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateCourseRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 200) String name,
        String description,
        @Size(max = 100) String level,
        @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal targetBand,
        @Positive Short totalSessions,
        @PositiveOrZero BigDecimal tuitionAmount,
        Boolean isPublic,
        UUID createdBy
) {}
