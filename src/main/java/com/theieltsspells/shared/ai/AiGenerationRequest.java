package com.theieltsspells.shared.ai;

import java.time.Duration;

public record AiGenerationRequest(
        String systemPrompt,
        String userPrompt,
        int maxOutputTokens,
        Double temperature,
        String responseMimeType,
        boolean disableReasoning,
        Duration timeout
) {
    public AiGenerationRequest {
        if (systemPrompt == null) systemPrompt = "";
        if (userPrompt == null) userPrompt = "";
        if (maxOutputTokens <= 0) throw new IllegalArgumentException("maxOutputTokens must be positive");
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }
}
