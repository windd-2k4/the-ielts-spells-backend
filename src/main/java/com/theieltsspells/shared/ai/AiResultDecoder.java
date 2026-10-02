package com.theieltsspells.shared.ai;

@FunctionalInterface
public interface AiResultDecoder<T> {
    T decode(AiGenerationResult result) throws Exception;
}
