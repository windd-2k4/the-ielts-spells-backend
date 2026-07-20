package com.theieltsspells.academic.application.dto;

import com.theieltsspells.shared.persistence.enums.ClassStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record UpdateClassRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull @Positive Short capacity,
        @NotNull LocalDate startsOn,
        LocalDate endsOn,
        @NotNull ClassStatus status,
        String defaultZoomUrl
) {}
