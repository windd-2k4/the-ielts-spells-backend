package com.theieltsspells.testing.infrastructure.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.CodedApiException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SpeachesSpeechClient {
    private static final String WAV_CONTENT_TYPE = "audio/wav";

    private final SpeechRuntimeProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient httpClient;

    @Autowired
    public SpeachesSpeechClient(SpeechRuntimeProperties properties, ObjectMapper objectMapper,
                                RestClient.Builder restClientBuilder) {
        this(properties, objectMapper, buildClient(properties, restClientBuilder));
    }

    SpeachesSpeechClient(SpeechRuntimeProperties properties, ObjectMapper objectMapper, RestClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    private static RestClient buildClient(SpeechRuntimeProperties properties, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutValue(properties.getConnectTimeout(), Duration.ofSeconds(3)));
        factory.setReadTimeout(timeoutValue(properties.getReadTimeout(), Duration.ofSeconds(120)));
        return builder
                .baseUrl(properties.getBaseUrl().replaceAll("/+$", ""))
                .requestFactory(factory)
                .build();
    }

    public Transcription transcribe(byte[] audio) {
        if (audio == null || audio.length == 0) throw new BusinessRuleException("Bản ghi âm Speaking bị rỗng");
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", properties.getSttModel());
        body.add("language", "en");
        body.add("response_format", "verbose_json");
        body.add("timestamp_granularities[]", "word");
        ByteArrayResource resource = new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return "speaking-response.webm";
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType("audio/webm"));
        body.add("file", new HttpEntity<>(resource, fileHeaders));
        byte[] response = exchange(() -> httpClient.post()
                .uri("/v1/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(byte[].class));
        try {
            JsonNode root = objectMapper.readTree(response);
            List<Word> words = new ArrayList<>();
            root.path("words").forEach(value -> words.add(new Word(
                    value.path("word").asText(), milliseconds(value.path("start").asDouble()),
                    milliseconds(value.path("end").asDouble()))));
            return new Transcription(root.path("text").asText(""), language(root.path("language").asText("en")),
                    milliseconds(root.path("duration").asDouble()), List.copyOf(words), null);
        } catch (IOException exception) {
            throw providerError("Speaches trả về transcript không hợp lệ");
        }
    }

    public SpeechAudio synthesize(String text) {
        if (text == null || text.isBlank()) throw new BusinessRuleException("Nội dung giọng examiner bị rỗng");
        if (text.length() > 4_096) throw new BusinessRuleException("Nội dung giọng examiner vượt quá 4096 ký tự");
        byte[] response = exchange(() -> httpClient.post()
                .uri("/v1/audio/speech")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", properties.getTtsModel(),
                        "voice", properties.getTtsVoice(),
                        "input", text.trim(),
                        "response_format", "wav"
                ))
                .retrieve()
                .body(byte[].class));
        if (response.length == 0) throw providerError("Speaches trả về audio rỗng");
        return new SpeechAudio(response, WAV_CONTENT_TYPE, "wav");
    }

    private byte[] exchange(HttpCall call) {
        try {
            byte[] response = call.execute();
            if (response == null) throw providerError("Speaches trả về dữ liệu rỗng");
            return response;
        } catch (RestClientResponseException exception) {
            throw providerError("Speaches từ chối yêu cầu (HTTP " + exception.getStatusCode().value() + ")");
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new CodedApiException(HttpStatus.GATEWAY_TIMEOUT, "SPEECH_SERVICE_TIMEOUT",
                        "Dịch vụ giọng nói phản hồi quá thời gian; vui lòng thử lại");
            }
            throw unavailable();
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException) return true;
            current = current.getCause();
        }
        return false;
    }

    private static Duration timeoutValue(Duration configured, Duration fallback) {
        if (configured == null || configured.isZero() || configured.isNegative()) return fallback;
        return configured.compareTo(Duration.ofMinutes(10)) > 0 ? Duration.ofMinutes(10) : configured;
    }

    private long milliseconds(double seconds) {
        return Math.max(0, Math.round(seconds * 1_000));
    }

    private String language(String value) {
        return "english".equalsIgnoreCase(value) ? "en" : value;
    }

    private CodedApiException unavailable() {
        return new CodedApiException(HttpStatus.SERVICE_UNAVAILABLE, "SPEECH_SERVICE_UNAVAILABLE",
                "Dịch vụ giọng nói tạm thời không khả dụng; vui lòng thử lại");
    }

    private CodedApiException providerError(String message) {
        return new CodedApiException(HttpStatus.BAD_GATEWAY, "SPEECH_SERVICE_ERROR", message);
    }

    @FunctionalInterface
    private interface HttpCall {
        byte[] execute();
    }

    public record Transcription(String text, String language, long durationMs, List<Word> words,
                                Double confidence) {}

    public record Word(String word, long startMs, long endMs) {}

    public record SpeechAudio(byte[] bytes, String contentType, String extension) {}
}
