package com.theieltsspells.testing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
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

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.speaking-evaluation.enabled", havingValue = "true", matchIfMissing = true)
public class SpeakingEvaluationWorker {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final SpeakingEvaluationService service;
    private final SpeakingAiEvaluator evaluator;
    private final PlatformTransactionManager transactionManager;
    private final com.theieltsspells.notification.WritingNotificationService notifications;

    @Value("${app.speaking-evaluation.daily-limit:3}")
    private int dailyLimit = 3;

    @Scheduled(fixedDelayString = "${app.speaking-evaluation.poll-millis:10000}")
    public void processNext() {
        Source source = claim();
        if (source == null) return;
        long started = System.nanoTime();
        try {
            var result = evaluator.evaluate(service.responseSources(source.attemptId()));
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> save(source, result, elapsed(started)));
        } catch (Exception exception) {
            log.warn("Speaking evaluation {} failed ({})", source.id(), exception.getClass().getSimpleName());
            String message = exception instanceof IllegalArgumentException ? exception.getMessage()
                    : "TIS chưa thể hoàn tất đánh giá Speaking. Giáo viên vẫn có thể chấm hoặc quản trị viên thử lại.";
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbc.update("""
                    update public.speaking_evaluations set status='FAILED', error_message=?, processing_ms=?, ai_requested_at=null,
                      processing_token=null, processing_started_at=null, updated_at=now()
                    where id=? and status='PROCESSING' and processing_token=?
                    """, message, elapsed(started), source.id(), source.token()));
        }
    }

    private Source claim() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.execute("select pg_advisory_xact_lock(845739103)");
            jdbc.update("""
                    update public.speaking_evaluations set status='FAILED', processing_token=null,
                      processing_started_at=null, error_message='Tác vụ chấm bị gián đoạn. Vui lòng thử lại.', updated_at=now()
                    where status='PROCESSING' and question_key=?
                      and processing_started_at < now() - interval '1 hour'
                    """, SpeakingEvaluationService.ATTEMPT_KEY);
            if (dailyLimit <= 0) return null;
            Long used = jdbc.queryForObject("""
                    select count(*) from public.speaking_evaluations where question_key=?
                      and ai_requested_at >= current_date and status in ('PROCESSING','AI_COMPLETED')
                    """, Long.class, SpeakingEvaluationService.ATTEMPT_KEY);
            if (used != null && used >= dailyLimit) return null;
            var rows = jdbc.query("""
                    select id, attempt_id from public.speaking_evaluations
                    where status='QUEUED' and question_key=? order by created_at
                    for update skip locked limit 1
                    """, (rs, ignored) -> new Source(rs.getObject("id", UUID.class),
                    rs.getObject("attempt_id", UUID.class), UUID.randomUUID()), SpeakingEvaluationService.ATTEMPT_KEY);
            if (rows.isEmpty()) return null;
            Source source = rows.getFirst();
            int changed = jdbc.update("""
                    update public.speaking_evaluations set status='PROCESSING', error_message=null,
                      processing_token=?, processing_started_at=now(), rubric_version=?, prompt_version=?, updated_at=now()
                    where id=? and status='QUEUED'
                    """, source.token(), SpeakingAiEvaluator.RUBRIC_VERSION,
                    SpeakingAiEvaluator.PROMPT_VERSION, source.id());
            return changed == 1 ? source : null;
        });
    }

    private void save(Source source, SpeakingAiEvaluator.Result value, int processingMs) {
        int changed = jdbc.update("""
                update public.speaking_evaluations set status='AI_COMPLETED', overall_band=?,
                  fluency_band=?, lexical_band=?, grammar_band=?, pronunciation_band=?,
                  fluency_feedback=?, lexical_feedback=?, grammar_feedback=?, pronunciation_feedback=?,
                  strengths=cast(? as jsonb), improvements=cast(? as jsonb), model_provider=?, model_name=?,
                  processing_ms=?, error_message=null, processing_token=null, processing_started_at=null,
                  ai_completed_at=now(), updated_at=now()
                where id=? and status='PROCESSING' and processing_token=?
                """, value.overall(), value.fluency().band(), value.lexical().band(), value.grammar().band(),
                value.pronunciation().band(), value.fluency().feedback(), value.lexical().feedback(),
                value.grammar().feedback(), value.pronunciation().feedback(), json(value.strengths()),
                json(value.improvements()), value.provider(), value.model(), processingMs, source.id(), source.token());
        if (changed == 1) notifications.speakingCompleted(source.id(), false);
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Cannot serialize Speaking evaluation", exception); }
    }
    private int elapsed(long started) { return Math.toIntExact((System.nanoTime() - started) / 1_000_000); }
    private record Source(UUID id, UUID attemptId, UUID token) {}
}
