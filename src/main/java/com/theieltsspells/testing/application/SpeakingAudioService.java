package com.theieltsspells.testing.application;

import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.storage.FileStorage;
import com.theieltsspells.testing.application.dto.SpeakingAudioUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SpeakingAudioService {
    private final JdbcTemplate jdbc;
    private final FileStorage storage;
    private final WebmAudioInspector inspector;

    @Value("${app.speaking-audio.max-upload-bytes:10485760}")
    private long maxUploadBytes;

    @Value("${app.speaking-audio.max-duration-seconds:180}")
    private long maxDurationSeconds;

    @Value("${app.speaking-audio.storage-bucket:speaking-audio-private}")
    private String storageBucket;

    @Transactional
    public SpeakingAudioUploadResponse upload(UUID attemptId, String questionKey, MultipartFile file, UUID studentId) {
        long started = System.nanoTime();
        if (questionKey == null || questionKey.isBlank() || questionKey.length() > 200) {
            throw new BusinessRuleException("Câu hỏi Speaking không hợp lệ");
        }
        assertOwnedSpeakingAttempt(attemptId, studentId, true);
        var inspection = inspector.inspect(file, maxUploadBytes, Duration.ofSeconds(maxDurationSeconds));
        String storageKey = "speaking-attempts/" + attemptId + "/" + UUID.randomUUID() + ".webm";
        storage.storeInBucket(storageBucket, storageKey, file);
        jdbc.update("""
                insert into public.speaking_audio_assets(
                  attempt_id, student_id, question_key, storage_key, mime_type, size_bytes, duration_ms)
                values (?, ?, ?, ?, ?, ?, ?)
                """, attemptId, studentId, questionKey.trim(), storageKey, inspection.mimeType(),
                inspection.sizeBytes(), inspection.durationMs());
        log.info("event=speaking_audio_upload attemptId={} questionKey={} uploadMs={} audioDurationMs={} sizeBytes={}",
                attemptId, questionKey.trim(), (System.nanoTime() - started) / 1_000_000,
                inspection.durationMs(), inspection.sizeBytes());
        return new SpeakingAudioUploadResponse(storageKey, audioPath(attemptId, questionKey.trim()),
                inspection.sizeBytes(), inspection.durationMs());
    }

    public void assertOwnedStorageKey(UUID attemptId, UUID studentId, String storageKey, String questionKey) {
        durationMsForOwnedStorageKey(attemptId, studentId, storageKey, questionKey);
    }

    public int durationMsForOwnedStorageKey(UUID attemptId, UUID studentId, String storageKey, String questionKey) {
        var values = jdbc.query("""
                select duration_ms from public.speaking_audio_assets
                where attempt_id=? and student_id=? and storage_key=? and question_key=?
                """, (rs, ignored) -> rs.getInt("duration_ms"), attemptId, studentId, storageKey, questionKey);
        if (values.isEmpty()) throw new BusinessRuleException("Bản ghi âm không thuộc lượt làm này");
        return values.getFirst();
    }

    public AudioContent openOwned(UUID attemptId, String questionKey, UUID studentId) {
        assertOwnedSpeakingAttempt(attemptId, studentId, false);
        return open(attemptId, questionKey);
    }

    public AudioContent open(UUID attemptId, String questionKey) {
        var rows = jdbc.query("""
                select asset.storage_key, asset.mime_type, asset.size_bytes
                from public.speaking_audio_assets asset
                where asset.attempt_id=? and asset.question_key=?
                order by asset.created_at desc
                limit 1
                """, (rs, ignored) -> new Asset(rs.getString("storage_key"), rs.getString("mime_type"),
                rs.getLong("size_bytes")), attemptId, questionKey);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy bản ghi âm Speaking");
        var asset = rows.getFirst();
        return new AudioContent(openStored(asset.storageKey()), asset.mimeType(), asset.sizeBytes());
    }

    public InputStream openStored(String storageKey) {
        return storage.openFromBucket(storageBucket, storageKey);
    }

    public static String audioPath(UUID attemptId, String questionKey) {
        return "/student/speaking/attempts/" + attemptId + "/audio/"
                + UriUtils.encodePathSegment(questionKey, StandardCharsets.UTF_8);
    }

    private void assertOwnedSpeakingAttempt(UUID attemptId, UUID studentId, boolean requireInProgress) {
        var rows = jdbc.query("""
                select attempt.status::text
                from public.test_attempts attempt
                join public.test_versions version on version.id=attempt.test_version_id
                where attempt.id=? and attempt.student_id=? and version.primary_skill='SPEAKING'
                """, (rs, ignored) -> rs.getString("status"), attemptId, studentId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy lượt làm bài Speaking");
        if (requireInProgress && !"IN_PROGRESS".equals(rows.getFirst())) {
            throw new BusinessRuleException("Bài làm đã hoàn thành hoặc hết hạn");
        }
    }

    public record AudioContent(InputStream inputStream, String mimeType, long sizeBytes) {}
    private record Asset(String storageKey, String mimeType, long sizeBytes) {}
}
