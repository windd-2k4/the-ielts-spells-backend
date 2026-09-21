package com.theieltsspells.shared.ai;

public class AiProviderException extends RuntimeException {

    public enum Kind {
        TRANSIENT,
        MODEL_UNAVAILABLE,
        PROVIDER_CONFIGURATION,
        INVALID_REQUEST,
        INVALID_RESPONSE
    }

    private final Kind kind;

    public AiProviderException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public AiProviderException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
