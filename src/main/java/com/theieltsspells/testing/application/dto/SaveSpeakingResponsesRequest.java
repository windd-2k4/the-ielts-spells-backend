package com.theieltsspells.testing.application.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record SaveSpeakingResponsesRequest(
        @NotNull List<Item> responses
) {
    public record Item(
            @NotNull String questionKey,
            String audioUrl,
            String transcript,
            Map<String, String> hintChoices,
            int clientRevision
    ) {}
}
