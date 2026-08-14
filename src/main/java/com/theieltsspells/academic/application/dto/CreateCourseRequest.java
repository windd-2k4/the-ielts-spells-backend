package com.theieltsspells.academic.application.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.time.LocalDate;
import com.theieltsspells.shared.persistence.enums.ClassStatus;
import com.theieltsspells.shared.persistence.enums.SkillPair;

public record CreateCourseRequest(
        UUID programId,
        @NotBlank @Size(max = 200) String name,
        String description,
        @Size(max = 100) String level,
        @NotNull SkillPair skillPair,
        @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal targetBand,
        @Positive Short totalSessions,
        @PositiveOrZero BigDecimal tuitionAmount,
        @NotNull @Positive Short capacity,
        @NotNull LocalDate startsOn,
        LocalDate endsOn,
        ClassStatus status,
        @Size(max = 1000) String defaultZoomUrl,
        Boolean isPublic,
        UUID createdBy
) {}
