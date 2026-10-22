package com.theieltsspells.shared.ai;

import java.time.Duration;
import java.util.List;

public record AiGenerationRequest(
        String systemPrompt,
        String userPrompt,
        int maxOutputTokens,
        Double temperature,
        String responseMimeType,
        boolean disableReasoning,
        Duration timeout,
        List<AiImageInput> images
) {
    public AiGenerationRequest {
        if (systemPrompt == null) systemPrompt = "";
        if (userPrompt == null) userPrompt = "";
        if (maxOutputTokens <= 0) throw new IllegalArgumentException("maxOutputTokens must be positive");
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        images = images == null ? List.of() : List.copyOf(images);
    }

    public AiGenerationRequest(String systemPrompt, String userPrompt, int maxOutputTokens, Double temperature,
                               String responseMimeType, boolean disableReasoning, Duration timeout) {
        this(systemPrompt, userPrompt, maxOutputTokens, temperature, responseMimeType, disableReasoning, timeout,
                List.of());
    }
}
