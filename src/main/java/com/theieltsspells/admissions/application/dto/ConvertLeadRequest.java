package com.theieltsspells.admissions.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConvertLeadRequest(@NotNull UUID studentId) {
}
