package com.theieltsspells.testing.infrastructure.speech;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.speech")
public class SpeechRuntimeProperties {
    private String baseUrl = "http://localhost:8000";
    private String sttModel = "Systran/faster-distil-whisper-small.en";
    private String ttsModel = "speaches-ai/Kokoro-82M-v1.0-ONNX";
    private String ttsVoice = "bf_emma";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(120);
}
