package com.theieltsspells.academic.application.dto;

import jakarta.validation.constraints.Size;

public record UpdateEnrollmentNoteRequest(
        @Size(max = 2000) String note
) {}
