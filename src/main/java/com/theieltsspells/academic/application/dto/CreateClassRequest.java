package com.theieltsspells.academic.application.dto;

import com.theieltsspells.shared.persistence.enums.ClassStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record CreateClassRequest(
        @NotNull UUID courseId,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull @Positive Short capacity,
        @NotNull LocalDate startsOn,
        LocalDate endsOn,
        ClassStatus status,
        String defaultZoomUrl,
        UUID createdBy
) {}
