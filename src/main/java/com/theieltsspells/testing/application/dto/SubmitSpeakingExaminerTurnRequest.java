package com.theieltsspells.testing.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SubmitSpeakingExaminerTurnRequest(
        @NotNull UUID clientTurnId,
        @NotNull UUID examinerTurnId,
        @PositiveOrZero long stateVersion,
        @NotBlank @Size(max = 300) String storageKey,
        @Size(max = 20_000) String transcript,
        @Positive int durationMs,
        SpeakingTurnEndReason endReason,
        @PositiveOrZero Integer clientUploadMs
) {
    public SubmitSpeakingExaminerTurnRequest(
            UUID clientTurnId,
            UUID examinerTurnId,
            long stateVersion,
            String storageKey,
            String transcript,
            int durationMs
    ) {
        this(clientTurnId, examinerTurnId, stateVersion, storageKey, transcript, durationMs, null, null);
    }
}
