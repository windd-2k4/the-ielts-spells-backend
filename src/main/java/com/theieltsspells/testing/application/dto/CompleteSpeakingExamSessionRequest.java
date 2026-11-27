package com.theieltsspells.testing.application.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record CompleteSpeakingExamSessionRequest(@PositiveOrZero long stateVersion) {}
