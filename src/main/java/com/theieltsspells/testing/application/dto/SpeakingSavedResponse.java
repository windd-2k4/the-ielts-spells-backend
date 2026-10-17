package com.theieltsspells.testing.application.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record SpeakingSavedResponse(
        String questionKey,
        String audioUrl,
        String transcript,
        Map<String, String> hintChoices,
        int clientRevision,
        OffsetDateTime answeredAt
) {}
