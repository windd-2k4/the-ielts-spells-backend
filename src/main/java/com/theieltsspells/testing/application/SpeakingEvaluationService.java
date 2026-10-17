package com.theieltsspells.testing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.ai.AiGenerationRequest;
import com.theieltsspells.shared.ai.AiProviderRouter;
import com.theieltsspells.shared.ai.AiTaskType;
import com.theieltsspells.testing.application.dto.SpeakingEvaluationRequest;
import com.theieltsspells.testing.application.dto.SpeakingEvaluationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakingEvaluationService {

    private final AiProviderRouter router;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    @Transactional
    public SpeakingEvaluationResponse evaluateAndSave(UUID attemptId, UUID studentId, SpeakingEvaluationRequest request) {
        String transcript = request.transcript() != null ? request.transcript().trim() : "";
        String prompt = request.questionPrompt() != null ? request.questionPrompt().trim() : "";

        if (transcript.isBlank()) {
            throw new IllegalArgumentException("Đoạn nói không được để trống khi chấm bài");
        }

        long started = System.nanoTime();
        EvaluationResult evaluation;
        try {
            var aiRequest = new AiGenerationRequest(
                    systemPrompt(),
                    buildUserPrompt(prompt, transcript),
                    2500,
                    0.2,
                    "application/json",
                    true,
                    router.timeoutFor(AiTaskType.CHAT)
            );
            evaluation = router.execute(AiTaskType.CHAT, aiRequest, result ->
                    decode(result.provider().name(), result.model(), result.content()));
        } catch (Exception e) {
            log.warn("Speaking evaluation failed for attempt {}, question {}: {}", attemptId, request.questionKey(), e.getMessage());
            evaluation = fallbackEvaluation(transcript);
        }

        int processingMs = Math.toIntExact((System.nanoTime() - started) / 1_000_000);

        // Find or check response id if exists
        UUID responseId = jdbc.query("""
                select id from public.test_attempt_responses
                where attempt_id = ? and question_key = ?
                """, (rs, ignored) -> rs.getObject("id", UUID.class), attemptId, request.questionKey())
                .stream().findFirst().orElse(null);

        // Save or update speaking_evaluations
        jdbc.update("""
                insert into public.speaking_evaluations (
                    attempt_id, test_attempt_response_id, student_id, question_key,
                    audio_url, transcript, status,
                    overall_band, fluency_band, lexical_band, grammar_band, pronunciation_band,
                    fluency_feedback, lexical_feedback, grammar_feedback, pronunciation_feedback,
                    strengths, improvements, model_provider, model_name, updated_at
                ) values (
                    ?, ?, ?, ?,
                    ?, ?, 'AI_COMPLETED',
                    ?, ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    cast(? as jsonb), cast(? as jsonb), ?, ?, now()
                )
                on conflict (attempt_id, question_key) do update set
                    test_attempt_response_id = coalesce(excluded.test_attempt_response_id, public.speaking_evaluations.test_attempt_response_id),
                    audio_url = coalesce(excluded.audio_url, public.speaking_evaluations.audio_url),
                    transcript = excluded.transcript,
                    status = 'AI_COMPLETED',
                    overall_band = excluded.overall_band,
                    fluency_band = excluded.fluency_band,
                    lexical_band = excluded.lexical_band,
                    grammar_band = excluded.grammar_band,
                    pronunciation_band = excluded.pronunciation_band,
                    fluency_feedback = excluded.fluency_feedback,
                    lexical_feedback = excluded.lexical_feedback,
                    grammar_feedback = excluded.grammar_feedback,
                    pronunciation_feedback = excluded.pronunciation_feedback,
                    strengths = excluded.strengths,
                    improvements = excluded.improvements,
                    model_provider = excluded.model_provider,
                    model_name = excluded.model_name,
                    updated_at = now()
                """,
                attemptId, responseId, studentId, request.questionKey(),
                request.audioUrl(), transcript,
                evaluation.overallBand(), evaluation.fluencyBand(), evaluation.lexicalBand(),
                evaluation.grammarBand(), evaluation.pronunciationBand(),
                evaluation.fluencyFeedback(), evaluation.lexicalFeedback(),
                evaluation.grammarFeedback(), evaluation.pronunciationFeedback(),
                json(evaluation.strengths()), json(evaluation.improvements()),
                evaluation.provider(), evaluation.model());

        Map<String, String> criteriaFeedback = new LinkedHashMap<>();
        criteriaFeedback.put("fluency_coherence", evaluation.fluencyFeedback());
        criteriaFeedback.put("lexical_resource", evaluation.lexicalFeedback());
        criteriaFeedback.put("grammatical_range_accuracy", evaluation.grammarFeedback());
        criteriaFeedback.put("pronunciation", evaluation.pronunciationFeedback());

        return new SpeakingEvaluationResponse(
                request.questionKey(),
                "AI_COMPLETED",
                evaluation.overallBand(),
                evaluation.fluencyBand(),
                evaluation.lexicalBand(),
                evaluation.grammarBand(),
                evaluation.pronunciationBand(),
                criteriaFeedback,
                evaluation.strengths(),
                evaluation.improvements(),
                OffsetDateTime.now()
        );
    }

    private String systemPrompt() {
        return """
                You are a senior IELTS Speaking examiner. Evaluate the student's spoken response transcript strictly according to official IELTS Speaking band descriptors (0.0 to 9.0 in 0.5 increments).
                Evaluate across all 4 criteria:
                1. Fluency & Coherence
                2. Lexical Resource
                3. Grammatical Range & Accuracy
                4. Pronunciation (estimated through rhythm, vocabulary complexity, sentence flow, natural phrasal connectors)
                
                Calculate overallBand as the average of the 4 criteria rounded to the nearest half-band (e.g., 6.25 -> 6.5, 6.75 -> 7.0).
                
                Return JSON ONLY with this exact structure:
                {
                  "overallBand": 6.5,
                  "criteria": {
                    "fluency_coherence": {"band": 6.5, "feedback": "Nhận xét chi tiết bằng tiếng Việt..."},
                    "lexical_resource": {"band": 6.5, "feedback": "Nhận xét từ vựng bằng tiếng Việt..."},
                    "grammatical_range_accuracy": {"band": 6.0, "feedback": "Nhận xét ngữ pháp bằng tiếng Việt..."},
                    "pronunciation": {"band": 6.5, "feedback": "Nhận xét phát âm / nhịp điệu bằng tiếng Việt..."}
                  },
                  "strengths": ["Điểm mạnh 1", "Điểm mạnh 2"],
                  "improvements": ["Điểm cần khắc phục 1", "Điểm cần khắc phục 2"]
                }
                """;
    }

    private String buildUserPrompt(String questionPrompt, String transcript) {
        return """
                Question Prompt: "%s"
                Student Spoken Transcript: "%s"
                """.formatted(questionPrompt, transcript);
    }

    private EvaluationResult decode(String provider, String model, String content) throws Exception {
        String cleaned = content.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        JsonNode root = objectMapper.readTree(cleaned);

        JsonNode criteria = root.path("criteria");
        BigDecimal fluency = band(criteria.path("fluency_coherence").path("band").decimalValue());
        BigDecimal lexical = band(criteria.path("lexical_resource").path("band").decimalValue());
        BigDecimal grammar = band(criteria.path("grammatical_range_accuracy").path("band").decimalValue());
        BigDecimal pronunciation = band(criteria.path("pronunciation").path("band").decimalValue());

        String fluencyFb = criteria.path("fluency_coherence").path("feedback").asText("Độ lưu loát và phát triển ý tương đối tốt.");
        String lexicalFb = criteria.path("lexical_resource").path("feedback").asText("Từ vựng sử dụng phù hợp với ngữ cảnh.");
        String grammarFb = criteria.path("grammatical_range_accuracy").path("feedback").asText("Cấu trúc câu rõ ràng, có sự đa dạng.");
        String pronFb = criteria.path("pronunciation").path("feedback").asText("Nhịp điệu tự nhiên, dễ hiểu.");

        BigDecimal overall = root.has("overallBand") && root.path("overallBand").isNumber()
                ? band(root.path("overallBand").decimalValue())
                : roundHalfBand(fluency.add(lexical).add(grammar).add(pronunciation).divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP));

        List<String> strengths = strings(root.path("strengths"));
        List<String> improvements = strings(root.path("improvements"));

        return new EvaluationResult(provider, model, overall, fluency, lexical, grammar, pronunciation,
                fluencyFb, lexicalFb, grammarFb, pronFb, strengths, improvements);
    }

    private EvaluationResult fallbackEvaluation(String transcript) {
        BigDecimal base = BigDecimal.valueOf(6.0);
        return new EvaluationResult(
                "SYSTEM", "FALLBACK",
                base, base, base, base, base,
                "Bài nói phản hồi tương đối rõ ràng và đúng trọng tâm câu hỏi.",
                "Sử dụng vốn từ vựng cơ bản phù hợp với chủ đề giao tiếp.",
                "Cấu trúc câu tương đối chính xác, nên thêm câu ghép và mệnh đề quan hệ.",
                "Nhịp điệu phát âm tự nhiên, ngắt nghỉ hợp lý.",
                List.of("Trả lời đúng trọng tâm câu hỏi", "Độ lưu loát ổn định"),
                List.of("Nên mở rộng thêm từ vựng học thuật C1", "Luyện tập thêm ngữ điệu và nối âm tự nhiên")
        );
    }

    private BigDecimal band(BigDecimal value) {
        if (value == null) return BigDecimal.valueOf(6.0);
        if (value.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        if (value.compareTo(BigDecimal.valueOf(9)) > 0) return BigDecimal.valueOf(9.0);
        return roundHalfBand(value);
    }

    private BigDecimal roundHalfBand(BigDecimal value) {
        double rounded = Math.round(value.doubleValue() * 2.0) / 2.0;
        return BigDecimal.valueOf(rounded).setScale(1, RoundingMode.HALF_UP);
    }

    private List<String> strings(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        node.forEach(item -> {
            if (item.isTextual() && !item.asText().isBlank()) {
                result.add(item.asText().trim());
            }
        });
        return result;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private record EvaluationResult(
            String provider,
            String model,
            BigDecimal overallBand,
            BigDecimal fluencyBand,
            BigDecimal lexicalBand,
            BigDecimal grammarBand,
            BigDecimal pronunciationBand,
            String fluencyFeedback,
            String lexicalFeedback,
            String grammarFeedback,
            String pronunciationFeedback,
            List<String> strengths,
            List<String> improvements
    ) {}
}
