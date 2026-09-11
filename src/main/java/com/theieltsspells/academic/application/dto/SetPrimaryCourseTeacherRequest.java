package com.theieltsspells.academic.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SetPrimaryCourseTeacherRequest(@NotNull UUID teacherId) {
}

