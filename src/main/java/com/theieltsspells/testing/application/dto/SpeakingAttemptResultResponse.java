package com.theieltsspells.testing.application.dto;

import com.theieltsspells.testing.domain.SpeakingSessionVariant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SpeakingAttemptResultResponse(
        UUID attemptId,
        String status,
        SpeakingSessionVariant variant,
        List<Integer> partsEvaluated,
        boolean overallBandAvailable,
        OffsetDateTime submittedAt,
        boolean resultVisible,
        boolean aiEligible,
        List<SpeakingPartResponse> parts,
        List<SpeakingSavedResponse> responses,
        List<SpeakingEvaluationResponse> evaluations
) {}
