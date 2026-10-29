package com.theieltsspells.assignment.application.dto;

import jakarta.validation.constraints.Size;

public record StudentSubmissionRequest(
        @Size(max = 100000) String textContent,
        boolean submit
) {}
