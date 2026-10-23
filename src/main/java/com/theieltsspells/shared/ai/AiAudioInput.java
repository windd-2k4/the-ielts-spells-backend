package com.theieltsspells.shared.ai;

import java.util.Base64;
import java.util.Set;

public record AiAudioInput(String mimeType, String base64Data) {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "audio/webm", "audio/ogg", "audio/mpeg", "audio/mp4", "audio/wav", "audio/x-wav"
    );

    public AiAudioInput {
        if (!SUPPORTED_TYPES.contains(mimeType)) throw new IllegalArgumentException("Unsupported AI audio type");
        if (base64Data == null || base64Data.isBlank()) throw new IllegalArgumentException("AI audio data is required");
    }

    public static AiAudioInput fromDataUrl(String value) {
        if (value == null || !value.startsWith("data:") || !value.contains(";base64,")) {
            throw new IllegalArgumentException("Speaking audio must be a base64 data URL");
        }
        int separator = value.indexOf(";base64,");
        String mimeType = value.substring(5, separator).toLowerCase();
        String data = value.substring(separator + 8);
        Base64.getDecoder().decode(data);
        return new AiAudioInput(mimeType, data);
    }
}
