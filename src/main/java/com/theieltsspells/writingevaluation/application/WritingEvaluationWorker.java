package com.theieltsspells.writingevaluation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.writingevaluation.infrastructure.WritingAiEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/** Durable database queue. Transactions never stay open during provider calls. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.writing-evaluation.enabled", havingValue = "true", matchIfMissing = true)
public class WritingEvaluationWorker {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final WritingAiEvaluator evaluator;
    private final PlatformTransactionManager transactionManager;
    private final com.theieltsspells.notification.WritingNotificationService notifications;

    @Value("${app.writing-evaluation.daily-limit:5}")
    private int dailyLimit = 5;

    @Scheduled(fixedDelayString = "${app.writing-evaluation.poll-millis:10000}")
    public void processNext() {
        var source = claim();
        if (source == null) return;
        long started = System.nanoTime();
        try {
            var result = evaluator.evaluate(source.taskType(), source.essay(), source.context());
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> save(source, result, elapsed(started)));
        } catch (Exception exception) {
            log.warn("Writing evaluation {} failed ({})", source.id(), exception.getClass().getSimpleName());
            String message = exception instanceof WritingAiEvaluator.MissingContextException ? exception.getMessage()
                    : "AI chưa thể hoàn tất đánh giá. Giáo viên có thể chấm thủ công hoặc thử lại.";
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbc.update("""
                    update public.writing_evaluations set status='FAILED', error_message=?, processing_ms=?,
                      processing_token=null, ai_requested_at=null, updated_at=now()
                    where id=? and status='PROCESSING' and processing_token=?
                    """, message, elapsed(started), source.id(), source.token()));
        }
    }

    private Source claim() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.execute("select pg_advisory_xact_lock(845739102)");
            // Interrupted work requires explicit retry or review, not an endless automatic loop.
            jdbc.update("""
                    update public.writing_evaluations set status='FAILED', processing_token=null,
                      processing_started_at=null, error_message='Tác vụ chấm bị gián đoạn. Vui lòng thử chấm lại.', updated_at=now()
                    where status='PROCESSING' and processing_started_at < now() - interval '1 hour'
                    """);
            if (dailyLimit <= 0) return null;
            var sources = jdbc.query("""
                    select id, task_type, essay_text, task_context::text from public.writing_evaluations
                    where status='QUEUED' and test_attempt_response_id is not null
                    order by created_at for update skip locked limit 1
                    """, (rs, ignored) -> new Source(rs.getObject("id", UUID.class), UUID.randomUUID(),
                    rs.getString("task_type"), rs.getString("essay_text"), rs.getString("task_context")));
            if (sources.isEmpty()) return null;
            var source = sources.getFirst();
            int reserved = jdbc.update("""
                    insert into public.writing_ai_daily_usage(usage_date, started_count)
                    values (current_date, 1)
                    on conflict (usage_date) do update
                      set started_count=public.writing_ai_daily_usage.started_count + 1, updated_at=now()
                    where public.writing_ai_daily_usage.started_count < ?
                    """, dailyLimit);
            if (reserved == 0) return null;
            int claimed = jdbc.update("""
                    update public.writing_evaluations set status='PROCESSING', error_message=null,
                      processing_token=?, processing_started_at=now(), prompt_version=?, rubric_version=?, updated_at=now()
                    where id=? and status='QUEUED'
                    """, source.token(), WritingAiEvaluator.PROMPT_VERSION, WritingAiEvaluator.RUBRIC_VERSION, source.id());
            if (claimed != 1) throw new IllegalStateException("Writing evaluation lease was not acquired");
            return source;
        });
    }

    private void save(Source source, WritingAiEvaluator.Result value, int processingMs) {
        int updated = jdbc.update("""
                update public.writing_evaluations set status='AI_COMPLETED', model_provider=?, model_name=?,
                  overall_band_ai=?, ai_strengths=cast(? as jsonb), ai_improvements=cast(? as jsonb),
                  detected_errors=cast(? as jsonb), raw_response=cast(? as jsonb), confidence=?,
                  processing_ms=?, error_message=null, processing_token=null, ai_completed_at=now(), updated_at=now()
                where id=? and status='PROCESSING' and processing_token=?
                """, value.provider(), value.model(), value.overallBand(), json(value.strengths()),
                json(value.improvements()), json(value.detectedErrors()), json(value.raw()),
                value.confidence(), processingMs, source.id(), source.token());
        // Lease recovery invalidates an old worker's ownership; teacher grading remains an independent stream.
        if (updated == 0) return;
        value.criteria().forEach((criterion, score) -> jdbc.update("""
                insert into public.writing_criterion_scores(evaluation_id, criterion, ai_band, ai_feedback, evidence)
                values (?, ?, ?, ?, cast(? as jsonb))
                on conflict (evaluation_id, criterion) do update set ai_band=excluded.ai_band,
                  ai_feedback=excluded.ai_feedback, evidence=excluded.evidence
                """, source.id(), criterion, score.band(), score.feedback(), json(score.evidence())));
        notifications.completed(source.id(), false);
    }

    private int elapsed(long started) { return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000); }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalArgumentException("Cannot serialize Writing result", exception); }
    }
    private record Source(UUID id, UUID token, String taskType, String essay, String context) { }
}
