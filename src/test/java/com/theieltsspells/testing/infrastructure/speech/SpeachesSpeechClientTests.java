package com.theieltsspells.testing.infrastructure.speech;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.CodedApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SpeachesSpeechClientTests {
    private MockRestServiceServer server;
    private SpeachesSpeechClient client;

    @BeforeEach
    void setUp() {
        SpeechRuntimeProperties properties = new SpeechRuntimeProperties();
        properties.setBaseUrl("http://speaches.test");
        properties.setConnectTimeout(Duration.ofMillis(200));
        properties.setReadTimeout(Duration.ofMillis(200));
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SpeachesSpeechClient(properties, new ObjectMapper(), builder.build());
    }

    @Test
    void transcribesWebmWithEnglishWordTimestamps() {
        server.expect(once(), requestTo("http://speaches.test/v1/audio/transcriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andRespond(withSuccess("""
                        {"text":"I live in Hanoi.","language":"english","duration":42.0,
                         "words":[{"word":"I","start":0.1,"end":0.2}]}
                        """, MediaType.APPLICATION_JSON));

        var result = client.transcribe(new byte[]{1, 2, 3});

        assertThat(result.text()).isEqualTo("I live in Hanoi.");
        assertThat(result.language()).isEqualTo("en");
        assertThat(result.durationMs()).isEqualTo(42_000);
        assertThat(result.words()).containsExactly(new SpeachesSpeechClient.Word("I", 100, 200));
        assertThat(result.confidence()).isNull();
        server.verify();
    }

    @Test
    void synthesizesExaminerPromptAsWav() {
        server.expect(once(), requestTo("http://speaches.test/v1/audio/speech"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"model":"speaches-ai/Kokoro-82M-v1.0-ONNX","voice":"bf_emma",
                         "input":"Good morning","response_format":"wav"}
                        """))
                .andRespond(withSuccess("RIFF-wave".getBytes(), MediaType.parseMediaType("audio/wav")));

        var result = client.synthesize("Good morning");

        assertThat(result.contentType()).isEqualTo("audio/wav");
        assertThat(result.extension()).isEqualTo("wav");
        assertThat(result.bytes()).startsWith("RIFF".getBytes());
        server.verify();
    }

    @Test
    void mapsReadTimeoutToGatewayTimeout() {
        server.expect(once(), requestTo("http://speaches.test/v1/audio/speech"))
                .andRespond(request -> {
                    throw new ResourceAccessException("timed out", new SocketTimeoutException("read timed out"));
                });

        assertThatThrownBy(() -> client.synthesize("Good morning"))
                .isInstanceOfSatisfying(CodedApiException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(504);
                    assertThat(exception.code()).isEqualTo("SPEECH_SERVICE_TIMEOUT");
                });
    }

    @Test
    void mapsStoppedServiceToServiceUnavailable() {
        server.expect(once(), requestTo("http://speaches.test/v1/audio/speech"))
                .andRespond(request -> {
                    throw new ResourceAccessException("connection refused", new ConnectException("refused"));
                });

        assertThatThrownBy(() -> client.synthesize("Good morning"))
                .isInstanceOfSatisfying(CodedApiException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(503);
                    assertThat(exception.code()).isEqualTo("SPEECH_SERVICE_UNAVAILABLE");
                });
    }
}
