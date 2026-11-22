package com.theieltsspells.testing.application.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record SpeakingExamPartTwoReadyRequest(@PositiveOrZero long stateVersion) {}
