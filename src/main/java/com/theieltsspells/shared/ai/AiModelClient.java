package com.theieltsspells.shared.ai;

public interface AiModelClient {
    AiProvider provider();

    boolean isConfigured();

    AiGenerationResult generate(String model, AiGenerationRequest request);
}
