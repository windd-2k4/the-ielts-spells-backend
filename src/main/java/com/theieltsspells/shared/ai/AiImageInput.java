package com.theieltsspells.shared.ai;

import java.util.Base64;
import java.util.Set;

public record AiImageInput(String mimeType, String base64Data) {
    private static final Set<String> SUPPORTED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    public AiImageInput {
        if (!SUPPORTED_TYPES.contains(mimeType)) throw new IllegalArgumentException("Unsupported AI image type");
        if (base64Data == null || base64Data.isBlank()) throw new IllegalArgumentException("AI image data is required");
    }

    public static AiImageInput fromBytes(String mimeType, byte[] data) {
        if (data == null || data.length == 0) throw new IllegalArgumentException("AI image data is required");
        return new AiImageInput(mimeType, Base64.getEncoder().encodeToString(data));
    }
}
