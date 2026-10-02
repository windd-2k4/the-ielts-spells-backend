package com.theieltsspells.writingevaluation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.ai.AiGenerationRequest;
import com.theieltsspells.shared.ai.AiProviderRouter;
import com.theieltsspells.shared.ai.AiTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Processes queued Writing feedback through the existing NVIDIA -> Gemini fallback router. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WritingEvaluationWorker {
    private static final List<String> SHARED_CRITERIA = List.of("coherence_cohesion", "lexical_resource", "grammatical_range_accuracy");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AiProviderRouter router;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(fixedDelayString = "${app.writing-evaluation.poll-millis:10000}")
    public void processNext() {
        UUID id = claim();
        if (id == null) return;
        long started = System.nanoTime();
        try {
            var source = jdbc.query("select task_type, essay_text from public.writing_evaluations where id=?",
                    (rs, ignored) -> new Source(rs.getString("task_type"), rs.getString("essay_text")), id).getFirst();
            var request = new AiGenerationRequest(systemPrompt(source.taskType()), source.essayText(), 3000, 0.1,
                    "application/json", true, router.timeoutFor(AiTaskType.CHAT));
            ModelEvaluation evaluation = router.execute(AiTaskType.CHAT, request, result ->
                    decode(result.provider().name(), result.model(), result.content(), source.taskType()));
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> save(id, evaluation,
                    Math.toIntExact((System.nanoTime() - started) / 1_000_000)));
        } catch (Exception exception) {
            log.warn("Writing evaluation {} failed: {}", id, exception.getMessage());
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbc.update("""
                    update public.writing_evaluations set status='FAILED', error_message=?, processing_ms=?, updated_at=now()
                    where id=?
                    """, safeMessage(exception), Math.toIntExact((System.nanoTime() - started) / 1_000_000), id));
        }
    }

    private UUID claim() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            var ids = jdbc.query("""
                    select id from public.writing_evaluations
                    where status='QUEUED' and test_attempt_response_id is not null
                    order by created_at for update skip locked limit 1
                    """, (rs, ignored) -> rs.getObject("id", UUID.class));
            if (ids.isEmpty()) return null;
            UUID id = ids.getFirst();
            jdbc.update("update public.writing_evaluations set status='PROCESSING', error_message=null, updated_at=now() where id=?", id);
            return id;
        });
    }

    private void save(UUID id, ModelEvaluation value, int processingMs) {
        jdbc.update("""
                update public.writing_evaluations set status='AI_COMPLETED', model_provider=?, model_name=?,
                  overall_band_ai=?, strengths=cast(? as jsonb), improvements=cast(? as jsonb),
                  detected_errors=cast(? as jsonb), raw_response=cast(? as jsonb), confidence=?,
                  processing_ms=?, error_message=null, updated_at=now() where id=?
                """, value.provider(), value.model(), value.overallBand(), json(value.strengths()),
                json(value.improvements()), json(value.detectedErrors()), json(value.raw()),
                value.confidence(), processingMs, id);
        value.criteria().forEach((criterion, score) -> jdbc.update("""
                insert into public.writing_criterion_scores(evaluation_id, criterion, ai_band, ai_feedback, evidence)
                values (?, ?, ?, ?, '[]'::jsonb)
                on conflict (evaluation_id, criterion) do update set ai_band=excluded.ai_band,
                  ai_feedback=excluded.ai_feedback
                """, id, criterion, score.band(), score.feedback()));
    }

    private ModelEvaluation decode(String provider, String model, String content, String taskType) throws Exception {
        String cleaned = content.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        JsonNode root = objectMapper.readTree(cleaned);
        BigDecimal overall = band(root.path("overallBand").decimalValue());
        var expected = new ArrayList<>(SHARED_CRITERIA);
        expected.addFirst("task_2".equals(taskType) ? "task_response" : "task_achievement");
        var scores = new LinkedHashMap<String, CriterionScore>();
        JsonNode criteria = root.path("criteria");
        for (String key : expected) {
            JsonNode item = criteria.path(key);
            if (!item.isObject()) throw new IllegalArgumentException("Missing Writing criterion: " + key);
            scores.put(key, new CriterionScore(band(item.path("band").decimalValue()), item.path("feedback").asText("")));
        }
        return new ModelEvaluation(provider, model, overall, scores, strings(root.path("strengths")),
                strings(root.path("improvements")), strings(root.path("detectedErrors")),
                root.path("confidence").isNumber() ? root.path("confidence").decimalValue() : null,
                objectMapper.convertValue(root, Map.class));
    }

    private BigDecimal band(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(9)) > 0
                || value.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Invalid IELTS band");
        }
        return value;
    }

    private List<String> strings(JsonNode node) {
        if (!node.isArray()) return List.of();
        var values = new ArrayList<String>();
        node.forEach(item -> { if (item.isTextual() && !item.asText().isBlank()) values.add(item.asText().trim()); });
        return List.copyOf(values);
    }

    private String systemPrompt(String taskType) {
        String achievement = "task_2".equals(taskType) ? "task_response" : "task_achievement";
        return """
                You are an IELTS Writing examiner. Evaluate the submitted essay conservatively using official IELTS criteria.
                Return JSON only with this exact shape:
                {"overallBand":6.5,"criteria":{"%s":{"band":6.5,"feedback":"..."},
                "coherence_cohesion":{"band":6.5,"feedback":"..."},"lexical_resource":{"band":6.5,"feedback":"..."},
                "grammatical_range_accuracy":{"band":6.5,"feedback":"..."}},"strengths":["..."],
                "improvements":["..."],"detectedErrors":["..."],"confidence":0.8}
                Bands must be 0-9 in 0.5 increments. Do not invent task facts that are absent from the essay.
                """.formatted(achievement);
    }

    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception exception) { throw new IllegalArgumentException(exception); } }
    private String safeMessage(Exception exception) { String value = exception.getMessage(); return value == null ? exception.getClass().getSimpleName() : value.substring(0, Math.min(value.length(), 900)); }
    private record Source(String taskType, String essayText) { }
    private record CriterionScore(BigDecimal band, String feedback) { }
    private record ModelEvaluation(String provider, String model, BigDecimal overallBand,
                                   Map<String, CriterionScore> criteria, List<String> strengths,
                                   List<String> improvements, List<String> detectedErrors,
                                   BigDecimal confidence, Map<String, Object> raw) { }
}
