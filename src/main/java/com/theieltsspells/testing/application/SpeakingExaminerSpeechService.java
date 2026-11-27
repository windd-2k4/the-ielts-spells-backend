package com.theieltsspells.testing.application;

import com.theieltsspells.shared.application.CodedApiException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.storage.FileStorage;
import com.theieltsspells.testing.infrastructure.speech.SpeachesSpeechClient;
import com.theieltsspells.testing.infrastructure.speech.SpeechRuntimeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SpeakingExaminerSpeechService {
    private final JdbcTemplate jdbc;
    private final SpeakingAudioService audioService;
    private final FileStorage storage;
    private final SpeachesSpeechClient speechClient;
    private final SpeechRuntimeProperties properties;

    @Value("${app.speaking-audio.storage-bucket:speaking-audio-private}")
    private String storageBucket;

    public SpeachesSpeechClient.Transcription transcribe(String storageKey) {
        return transcribeTimed(storageKey).transcription();
    }

    public TimedTranscription transcribeTimed(String storageKey) {
        long readStarted = System.nanoTime();
        try (InputStream input = audioService.openStored(storageKey)) {
            byte[] bytes = input.readAllBytes();
            long storageReadMs = elapsedMs(readStarted);
            long sttStarted = System.nanoTime();
            var transcription = speechClient.transcribe(bytes);
            return new TimedTranscription(transcription, storageReadMs, elapsedMs(sttStarted));
        } catch (IOException exception) {
            throw new CodedApiException(HttpStatus.BAD_GATEWAY, "SPEECH_AUDIO_READ_FAILED",
                    "Không thể đọc bản ghi âm để nhận dạng giọng nói");
        }
    }

    @Transactional
    public PromptAudio promptAudio(String prompt) {
        String cacheKey = sha256(properties.getTtsModel() + "\n" + properties.getTtsVoice() + "\n" + prompt.trim());
        var cached = find(cacheKey);
        if (cached != null) {
            log.debug("event=speaking_tts_cache_hit cacheKey={}", cacheKey);
            return cached;
        }

        long started = System.nanoTime();
        var generated = speechClient.synthesize(prompt);
        String storageKey = "speaking-examiner/tts/" + cacheKey + "." + generated.extension();
        storage.storeBytesInBucket(storageBucket, storageKey, generated.bytes(), generated.contentType());
        jdbc.update("""
                insert into public.speaking_examiner_tts_assets(
                  cache_key, model, voice, prompt_hash, storage_key, mime_type, size_bytes)
                values (?, ?, ?, ?, ?, ?, ?) on conflict (cache_key) do nothing
                """, cacheKey, properties.getTtsModel(), properties.getTtsVoice(), sha256(prompt.trim()),
                storageKey, generated.contentType(), generated.bytes().length);
        PromptAudio stored = find(cacheKey);
        if (stored == null) throw new IllegalStateException("Speaking examiner TTS cache insert failed");
        log.info("event=speaking_tts_cache_miss cacheKey={} ttsProcessingMs={} sizeBytes={}",
                cacheKey, elapsedMs(started), generated.bytes().length);
        return stored;
    }

    public AudioContent openPrompt(UUID sessionId, UUID turnId, UUID studentId) {
        var values = jdbc.query("""
                select asset.cache_key, asset.storage_key, asset.mime_type, asset.size_bytes
                from public.speaking_exam_turns turn
                join public.speaking_exam_sessions session on session.id=turn.session_id
                join public.speaking_examiner_tts_assets asset on asset.cache_key=turn.prompt_audio_cache_key
                where session.id=? and turn.id=? and session.student_id=?
                """, (rs, ignored) -> new PromptAudio(rs.getString("cache_key"), rs.getString("storage_key"),
                rs.getString("mime_type"), rs.getLong("size_bytes")), sessionId, turnId, studentId);
        if (values.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy audio câu hỏi examiner");
        PromptAudio value = values.getFirst();
        return new AudioContent(storage.openFromBucket(storageBucket, value.storageKey()),
                value.mimeType(), value.sizeBytes());
    }

    public String modelVersion() {
        return "speaches:stt=" + properties.getSttModel() + ";tts=" + properties.getTtsModel()
                + ";voice=" + properties.getTtsVoice();
    }

    public static String audioPath(UUID sessionId, UUID turnId) {
        return "/student/speaking/exam-sessions/" + sessionId + "/turns/" + turnId + "/audio";
    }

    private PromptAudio find(String cacheKey) {
        var values = jdbc.query("""
                select cache_key, storage_key, mime_type, size_bytes from public.speaking_examiner_tts_assets
                where cache_key=?
                """, (rs, ignored) -> new PromptAudio(rs.getString("cache_key"), rs.getString("storage_key"),
                rs.getString("mime_type"), rs.getLong("size_bytes")), cacheKey);
        return values.isEmpty() ? null : values.getFirst();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    public record PromptAudio(String cacheKey, String storageKey, String mimeType, long sizeBytes) {}

    public record AudioContent(InputStream inputStream, String mimeType, long sizeBytes) {}

    public record TimedTranscription(SpeachesSpeechClient.Transcription transcription,
                                     long storageReadMs, long sttProcessingMs) {}
}
