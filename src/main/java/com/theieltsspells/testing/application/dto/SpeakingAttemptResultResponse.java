package com.theieltsspells.testing.application.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SpeakingAttemptResultResponse(
        UUID attemptId,
        String status,
        OffsetDateTime submittedAt,
        boolean resultVisible,
        List<SpeakingPartResponse> parts,
        List<SpeakingSavedResponse> responses,
        List<SpeakingEvaluationResponse> evaluations
) {}
