package com.theieltsspells.testing.application.dto;

import jakarta.validation.constraints.NotBlank;

public record TestStatusRequest(@NotBlank String status) {}
