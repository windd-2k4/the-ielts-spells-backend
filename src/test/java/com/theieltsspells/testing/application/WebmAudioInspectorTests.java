package com.theieltsspells.testing.application;

import com.theieltsspells.shared.application.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebmAudioInspectorTests {
    private final WebmAudioInspector inspector = new WebmAudioInspector();

    @Test
    void acceptsAudioOnlyWebmOpusAndReadsServerSideDuration() {
        byte[] webm = webm("A_OPUS", false);
        var file = new MockMultipartFile("file", "ignored.exe", "audio/webm;codecs=opus", webm);

        var result = inspector.inspect(file, 1_000_000, Duration.ofSeconds(2));

        assertThat(result.mimeType()).isEqualTo("audio/webm");
        assertThat(result.sizeBytes()).isEqualTo(webm.length);
        assertThat(result.durationMs()).isEqualTo(1_020);
    }

    @Test
    void rejectsClientMimeSizeDurationAndVideoTracks() {
        byte[] audio = webm("A_OPUS", false);
        assertThatThrownBy(() -> inspector.inspect(
                new MockMultipartFile("file", "voice.webm", "application/octet-stream", audio),
                1_000_000, Duration.ofSeconds(2))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> inspector.inspect(
                new MockMultipartFile("file", "voice.webm", "audio/webm", audio),
                audio.length - 1L, Duration.ofSeconds(2))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> inspector.inspect(
                new MockMultipartFile("file", "voice.webm", "audio/webm", audio),
                1_000_000, Duration.ofSeconds(1))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> inspector.inspect(
                new MockMultipartFile("file", "voice.webm", "audio/webm", webm("A_OPUS", true)),
                1_000_000, Duration.ofSeconds(2))).isInstanceOf(BusinessRuleException.class);
    }

    private byte[] webm(String codec, boolean video) {
        byte[] header = element(bytes(0x1A, 0x45, 0xDF, 0xA3),
                element(bytes(0x42, 0x82), "webm".getBytes()));
        byte[] info = element(bytes(0x15, 0x49, 0xA9, 0x66), concat(
                element(bytes(0x2A, 0xD7, 0xB1), bytes(0x0F, 0x42, 0x40)),
                element(bytes(0x44, 0x89), ByteBuffer.allocate(8).putDouble(1_000d).array())));
        byte[] audioTrack = element(bytes(0xAE), concat(
                element(bytes(0xD7), bytes(1)),
                element(bytes(0x83), bytes(2)),
                element(bytes(0x86), codec.getBytes())));
        byte[] videoTrack = video ? element(bytes(0xAE), concat(
                element(bytes(0xD7), bytes(2)), element(bytes(0x83), bytes(1)),
                element(bytes(0x86), "V_VP8".getBytes()))) : new byte[0];
        byte[] tracks = element(bytes(0x16, 0x54, 0xAE, 0x6B), concat(audioTrack, videoTrack));
        byte[] cluster = element(bytes(0x1F, 0x43, 0xB6, 0x75), concat(
                element(bytes(0xE7), bytes(0)),
                element(bytes(0xA3), bytes(0x81, 0x03, 0x84, 0x80, 0x00))));
        return concat(header, element(bytes(0x18, 0x53, 0x80, 0x67), concat(info, tracks, cluster)));
    }

    private byte[] element(byte[] id, byte[] payload) {
        return concat(id, size(payload.length), payload);
    }

    private byte[] size(int value) {
        if (value < 127) return bytes(0x80 | value);
        if (value < 16_383) return bytes(0x40 | value >> 8, value);
        throw new IllegalArgumentException("fixture too large");
    }

    private byte[] concat(byte[]... values) {
        var output = new ByteArrayOutputStream();
        for (byte[] value : values) output.writeBytes(value);
        return output.toByteArray();
    }

    private byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }
}
