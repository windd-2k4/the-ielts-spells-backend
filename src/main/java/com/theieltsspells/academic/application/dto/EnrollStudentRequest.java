package com.theieltsspells.academic.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record EnrollStudentRequest(
        @NotNull UUID classId,
        @NotNull UUID studentId,
        @Size(max = 2000) String notes
) {}
