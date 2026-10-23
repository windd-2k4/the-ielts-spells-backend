package com.theieltsspells.shared.ai;

public interface AiModelClient {
    AiProvider provider();

    boolean isConfigured();

    default boolean supportsImages() {
        return false;
    }

    default boolean supportsAudio() {
        return false;
    }

    AiGenerationResult generate(String model, AiGenerationRequest request);
}
