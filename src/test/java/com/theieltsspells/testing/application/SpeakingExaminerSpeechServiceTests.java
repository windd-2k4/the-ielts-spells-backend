package com.theieltsspells.testing.application;

import com.theieltsspells.shared.storage.FileStorage;
import com.theieltsspells.testing.infrastructure.speech.SpeachesSpeechClient;
import com.theieltsspells.testing.infrastructure.speech.SpeechRuntimeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeakingExaminerSpeechServiceTests {
    @Mock JdbcTemplate jdbc;
    @Mock SpeakingAudioService audioService;
    @Mock FileStorage storage;
    @Mock SpeachesSpeechClient speechClient;
    private SpeechRuntimeProperties properties;
    private SpeakingExaminerSpeechService service;

    @BeforeEach
    void setUp() {
        properties = new SpeechRuntimeProperties();
        service = new SpeakingExaminerSpeechService(jdbc, audioService, storage, speechClient, properties);
        ReflectionTestUtils.setField(service, "storageBucket", "speaking-audio-private");
    }

    @Test
    void transcribesThePrivateStoredRecording() {
        byte[] webm = new byte[]{1, 2, 3};
        var expected = new SpeachesSpeechClient.Transcription(
                "I live in Hanoi.", "en", 2_000, List.of(), null);
        when(audioService.openStored("attempt/answer.webm")).thenReturn(new ByteArrayInputStream(webm));
        when(speechClient.transcribe(eq(webm))).thenReturn(expected);

        var result = service.transcribe("attempt/answer.webm");

        assertThat(result).isEqualTo(expected);
        verify(audioService).openStored("attempt/answer.webm");
    }

    @Test
    @SuppressWarnings("unchecked")
    void storesGeneratedPromptAndReturnsTheDurableCacheEntry() {
        byte[] wav = "RIFF-wave".getBytes();
        var cached = new SpeakingExaminerSpeechService.PromptAudio(
                "cache-key", "speaking-examiner/tts/cached.wav", "audio/wav", wav.length);
        when(jdbc.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(List.of(), List.of(cached));
        when(speechClient.synthesize("Good morning"))
                .thenReturn(new SpeachesSpeechClient.SpeechAudio(wav, "audio/wav", "wav"));

        var result = service.promptAudio("Good morning");

        assertThat(result).isEqualTo(cached);
        verify(storage).storeBytesInBucket(eq("speaking-audio-private"),
                anyString(), eq(wav), eq("audio/wav"));
        verify(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reusesCachedPromptWithoutCallingSpeaches() {
        var cached = new SpeakingExaminerSpeechService.PromptAudio(
                "cache-key", "speaking-examiner/tts/cached.wav", "audio/wav", 42);
        when(jdbc.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(List.of(cached));

        assertThat(service.promptAudio("Good morning")).isEqualTo(cached);
        verify(jdbc).query(anyString(), any(RowMapper.class), anyString());
    }

    @Test
    void derivesTheStoredSpeechVersionFromRuntimeConfiguration() {
        assertThat(service.modelVersion()).isEqualTo(
                "speaches:stt=Systran/faster-distil-whisper-small.en;"
                        + "tts=speaches-ai/Kokoro-82M-v1.0-ONNX;voice=bf_emma");
    }
}
