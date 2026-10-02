package com.theieltsspells.shared.ai;

public record AiGenerationResult(
        AiProvider provider,
        String model,
        String content
) {
}
