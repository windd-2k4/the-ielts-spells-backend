package com.theieltsspells.testing.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/** Persistently transcribes deterministic examiner turns after the next question has already been returned. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.speaking-examiner.background-transcription-enabled",
        havingValue = "true", matchIfMissing = true)
public class SpeakingBackgroundTranscriptionWorker {
    private final JdbcTemplate jdbc;
    private final SpeakingExaminerSpeechService speechService;
    private final SpeakingEvaluationService evaluationService;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.speaking-examiner.transcription-max-attempts:3}")
    private int maxAttempts;

    @Scheduled(
            fixedDelayString = "${app.speaking-examiner.transcription-poll-millis:500}",
            initialDelayString = "${app.speaking-examiner.transcription-initial-delay-millis:500}")
    public void processNext() {
        Source source = claim();
        if (source == null) return;
        long started = System.nanoTime();
        try {
            var timed = speechService.transcribeTimed(source.storageKey());
            String transcript = timed.transcription().text().trim();
            int changed = jdbc.update("""
                    update public.speaking_exam_turns set transcript=?, transcription_status='COMPLETED',
                      transcription_storage_read_ms=?, transcription_processing_ms=?,
                      transcription_token=null, transcription_started_at=null,
                      transcription_next_retry_at=null, transcription_error=null
                    where id=? and transcription_status='PROCESSING' and transcription_token=?
                    """, transcript, timed.storageReadMs(), timed.sttProcessingMs(), source.turnId(), source.token());
            if (changed == 1) {
                log.info("event=speaking_background_transcription_completed turnId={} queueMs={} "
                                + "storageReadMs={} sttProcessingMs={} totalMs={}",
                        source.turnId(), source.queueMs(), timed.storageReadMs(), timed.sttProcessingMs(),
                        elapsedMs(started));
                refreshEvaluationTranscriptWhenReady(source.attemptId());
            }
        } catch (RuntimeException exception) {
            fail(source, exception);
        }
    }

    private Source claim() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.update("""
                    update public.speaking_exam_turns set transcription_status='PENDING',
                      transcription_token=null, transcription_started_at=null,
                      transcription_next_retry_at=now(), transcription_error='Recovered interrupted transcription'
                    where transcription_status='PROCESSING'
                      and transcription_started_at < now() - interval '5 minutes'
                    """);
            var rows = jdbc.query("""
                    select turn.id, session.attempt_id, turn.answer_storage_key,
                      turn.transcription_attempts,
                      greatest(0, extract(epoch from (now() - turn.submitted_at)) * 1000)::bigint queue_ms
                    from public.speaking_exam_turns turn
                    join public.speaking_exam_sessions session on session.id=turn.session_id
                    where turn.transcription_status='PENDING'
                      and (turn.transcription_next_retry_at is null or turn.transcription_next_retry_at <= now())
                    order by turn.submitted_at
                    for update of turn skip locked limit 1
                    """, (rs, ignored) -> new Source(
                    rs.getObject("id", UUID.class),
                    rs.getObject("attempt_id", UUID.class),
                    rs.getString("answer_storage_key"),
                    rs.getInt("transcription_attempts") + 1,
                    UUID.randomUUID(),
                    rs.getLong("queue_ms")));
            if (rows.isEmpty()) return null;
            Source source = rows.getFirst();
            int changed = jdbc.update("""
                    update public.speaking_exam_turns set transcription_status='PROCESSING',
                      transcription_attempts=?, transcription_token=?, transcription_started_at=now(),
                      transcription_error=null
                    where id=? and transcription_status='PENDING'
                    """, source.attempt(), source.token(), source.turnId());
            return changed == 1 ? source : null;
        });
    }

    private void fail(Source source, RuntimeException exception) {
        boolean retry = source.attempt() < Math.max(1, maxAttempts);
        jdbc.update("""
                update public.speaking_exam_turns set transcription_status=?, transcription_token=null,
                  transcription_started_at=null,
                  transcription_next_retry_at=case when ? then now() + (? * interval '1 second') else null end,
                  transcription_error=?
                where id=? and transcription_status='PROCESSING' and transcription_token=?
                """, retry ? "PENDING" : "FAILED", retry, Math.min(30, 1 << source.attempt()),
                conciseMessage(exception), source.turnId(), source.token());
        log.warn("event=speaking_background_transcription_failed turnId={} attempt={} retry={} reason={}",
                source.turnId(), source.attempt(), retry, exception.getClass().getSimpleName());
    }

    private void refreshEvaluationTranscriptWhenReady(UUID attemptId) {
        Integer pending = jdbc.queryForObject("""
                select count(*) from public.speaking_exam_turns turn
                join public.speaking_exam_sessions session on session.id=turn.session_id
                where session.attempt_id=? and turn.transcription_status in ('PENDING','PROCESSING')
                """, Integer.class, attemptId);
        if (pending != null && pending == 0) evaluationService.refreshRegisteredTranscript(attemptId);
    }

    private String conciseMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private record Source(UUID turnId, UUID attemptId, String storageKey, int attempt, UUID token, long queueMs) {}
}
