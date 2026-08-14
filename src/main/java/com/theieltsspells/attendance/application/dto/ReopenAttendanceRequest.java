package com.theieltsspells.attendance.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReopenAttendanceRequest(
        @NotBlank @Size(max = 500) String reason
) {}
