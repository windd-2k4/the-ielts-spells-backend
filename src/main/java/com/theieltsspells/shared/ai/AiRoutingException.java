package com.theieltsspells.shared.ai;

public class AiRoutingException extends RuntimeException {
    public AiRoutingException(String message) {
        super(message);
    }

    public AiRoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}
