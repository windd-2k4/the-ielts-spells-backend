package com.theieltsspells.testing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.ai.AiAudioInput;
import com.theieltsspells.shared.ai.AiGenerationRequest;
import com.theieltsspells.shared.ai.AiProviderRouter;
import com.theieltsspells.shared.ai.AiTaskType;
import com.theieltsspells.shared.SpeakingBandScorePolicy;
import com.theieltsspells.testing.application.dto.SpeakingAiAnalytics.AnswerUpgrade;
import com.theieltsspells.testing.application.dto.SpeakingAiAnalytics.PronunciationIssue;
import com.theieltsspells.testing.application.dto.SpeakingAiAnalytics.SpeechMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SpeakingAiEvaluator {
    public static final String RUBRIC_VERSION = "ielts-speaking-public-v1";
    public static final String PROMPT_VERSION = "speaking-evaluator-v4";
    private static final List<String> FILLERS = List.of("um", "uh", "erm", "you know", "i mean");
    private final AiProviderRouter router;
    private final ObjectMapper objectMapper;
    private final SpeakingAudioService audioService;

    public Result evaluate(List<SpeakingEvaluationService.ResponseSource> sources) {
        List<AiAudioInput> audio = new ArrayList<>();
        long encodedBytes = 0;
        for (var source : sources) {
            AiAudioInput input = audio(source);
            if (input == null) continue;
            encodedBytes += input.base64Data().length();
            if (encodedBytes > 24_000_000L) {
                throw new IllegalArgumentException("Tổng dung lượng ghi âm vượt giới hạn xử lý AI; giáo viên vẫn có thể chấm thủ công");
            }
            audio.add(input);
        }
        if (audio.isEmpty()) throw new IllegalArgumentException("Bài Speaking chưa có file ghi âm để chấm Pronunciation");
        String transcript = sources.stream().filter(value -> value.transcript() != null)
                .map(value -> value.questionKey() + ": " + value.transcript())
                .reduce((left, right) -> left + "\n\n" + right).orElse("Không có transcript");
        var request = new AiGenerationRequest(systemPrompt(), "TRANSCRIPT THEO TỪNG CÂU:\n" + transcript,
                3200, 0.1, "application/json", true, router.timeoutFor(AiTaskType.CHAT), List.of(), audio);
        return router.execute(AiTaskType.CHAT, request,
                value -> decode(value.provider().name(), value.model(), value.content(), sources));
    }

    private AiAudioInput audio(SpeakingEvaluationService.ResponseSource source) {
        if (source.storageKey() != null) {
            try (var input = audioService.openStored(source.storageKey())) {
                return AiAudioInput.fromBytes("audio/webm", input.readAllBytes());
            } catch (IOException exception) {
                throw new IllegalArgumentException("Không thể đọc bản ghi âm Speaking", exception);
            }
        }
        return source.legacyAudioUrl() == null ? null : AiAudioInput.fromDataUrl(source.legacyAudioUrl());
    }

    private String systemPrompt() {
        return """
                Bạn là giám khảo IELTS Speaking. Nghe toàn bộ audio theo đúng thứ tự; transcript chỉ hỗ trợ đọc nội dung.
                Chấm toàn bộ bài thi theo bốn tiêu chí IELTS có trọng số bằng nhau: Fluency and Coherence,
                Lexical Resource, Grammatical Range and Accuracy, Pronunciation. Dùng band 0-9 theo bước 0.5.
                Áp dụng các mốc mô tả công khai hiện hành sau:
                - Fluency & Coherence: band 9 nói trôi chảy, phát triển chủ đề đầy đủ, ngập ngừng chỉ để chuẩn bị ý;
                  8 rất trôi chảy với lặp/tự sửa hiếm; 7 nói dài dễ dàng và liên kết linh hoạt dù đôi lúc ngập ngừng;
                  6 duy trì lượt nói nhưng đôi lúc mất mạch do ngập ngừng/lặp, dùng được nhiều từ nối; 5 thường duy trì
                  được nhưng chậm, lặp/tự sửa và dùng từ nối thiếu linh hoạt; 4 có nhiều khoảng dừng, liên kết cơ bản;
                  3 câu trả lời ngắn, nhiều khoảng dừng; 2 chỉ nói rời rạc; 1 gần như không giao tiếp; 0 không có mẫu nói.
                - Lexical Resource: band 9 dùng vốn từ đầy đủ, chính xác, tự nhiên; 8 vốn từ rộng, linh hoạt, thành ngữ
                  tốt với lỗi hiếm; 7 linh hoạt, có từ ít phổ biến và paraphrase hiệu quả; 6 đủ rộng để bàn chủ đề dài,
                  paraphrase nhìn chung thành công nhưng có chọn từ chưa phù hợp; 5 đủ cho chủ đề quen thuộc, linh hoạt
                  hạn chế; 4 chỉ đủ cho chủ đề quen thuộc, lỗi chọn từ thường xuyên; 3 chủ yếu từ đơn giản; 2 từ rời rạc;
                  1 không có vốn từ giao tiếp; 0 không có mẫu nói.
                - Grammatical Range & Accuracy: band 9 đầy đủ cấu trúc và gần như luôn chính xác; 8 cấu trúc rộng,
                  đa số câu không lỗi; 7 nhiều cấu trúc phức, thường tạo câu không lỗi; 6 kết hợp câu đơn/phức với độ
                  chính xác hợp lý nhưng cấu trúc phức còn lỗi; 5 dùng câu cơ bản khá chính xác, cấu trúc phức hạn chế;
                  4 chỉ có ít cấu trúc, lỗi thường xuyên; 3 chủ yếu mẫu câu cơ bản học thuộc; 2 không tạo được câu cơ bản;
                  1 không có ngôn ngữ để đánh giá; 0 không có mẫu nói.
                - Pronunciation: band 9 kiểm soát đầy đủ các đặc trưng âm vị và luôn dễ hiểu; 8 dùng rộng, duy trì tốt,
                  accent rất ít ảnh hưởng; 7 có đầy đủ điểm tích cực của band 6 và một phần band 8; 6 dùng được nhiều
                  đặc trưng nhưng kiểm soát chưa bền vững, nhìn chung dễ hiểu; 5 có điểm tích cực của band 4 và một phần
                  band 6; 4 phạm vi hạn chế, lỗi phát âm thường gây khó hiểu; 3 có một số điểm của band 2 và 4; 2 lời nói
                  thường khó hiểu; 1 hầu như không hiểu được; 0 không có mẫu nói.
                Pronunciation bắt buộc dựa trên audio: độ dễ hiểu, âm riêng lẻ, trọng âm, nhịp điệu, nối âm và ngữ điệu.
                Không suy đoán phát âm từ transcript.
                Đồng thời phân tích các chỉ số khẩu ngữ (speech_metrics) và đề xuất nâng cấp câu trả lời (answer_upgrades):
                - speech_metrics: chỉ ước tính pause_count (số lần ngập ngừng hoặc khoảng lặng bất thường > 1.5s)
                  và pronunciation_issues (các từ phát âm lỗi, gồm word, ipa chuẩn, và issue mô tả lỗi ngắn gọn).
                  Không tạo words_per_minute hoặc filler_words; server tính hai chỉ số này theo quy tắc xác định.
                  IPA chỉ là phiên âm chuẩn. Không suy đoán hay tạo "actual IPA" của học viên.
                - answer_upgrades: với từng câu hỏi trong transcript, trích 1 câu học viên đã trả lời (original_text), viết lại câu đó ở mức Band 7.5+ tự nhiên và học thuật hơn (upgraded_text), trích các collocations đắt giá được dùng, và explanation giải thích ngắn gọn bằng tiếng Việt.
                Mỗi nhận xét phải nêu bằng chứng quan sát được, bằng tiếng Việt. Chỉ trả JSON:
                {"criteria":{"fluency_coherence":{"band":6.0,"feedback":"..."},
                "lexical_resource":{"band":6.0,"feedback":"..."},
                "grammatical_range_accuracy":{"band":6.0,"feedback":"..."},
                "pronunciation":{"band":6.0,"feedback":"..."}},
                "speech_metrics":{
                  "pause_count":3,
                  "pronunciation_issues":[{"word":"comfortable","ipa":"ˈkʌmftəbl","issue":"Nuốt âm hoặc sai trọng âm"}]
                },
                "answer_upgrades":[
                  {
                    "question_key":"part1_q1",
                    "original_text":"I like books because it good for brain.",
                    "upgraded_text":"I have a keen interest in reading because it helps broaden my horizons.",
                    "collocations":["broaden my horizons","keen interest in"],
                    "explanation":"Dùng collocation Band 7.5+ thay cho từ vựng cơ bản và sửa lỗi ngữ pháp."
                  }
                ],
                "strengths":["..."],"improvements":["..."]}
                """;
    }

    private Result decode(String provider, String model, String content,
                          List<SpeakingEvaluationService.ResponseSource> sources) throws Exception {
        String cleaned = content.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        JsonNode root = objectMapper.readTree(cleaned);
        JsonNode criteria = root.path("criteria");
        Criterion fluency = criterion(criteria, "fluency_coherence");
        Criterion lexical = criterion(criteria, "lexical_resource");
        Criterion grammar = criterion(criteria, "grammatical_range_accuracy");
        Criterion pronunciation = criterion(criteria, "pronunciation");
        BigDecimal overall = SpeakingBandScorePolicy.overall(List.of(
                fluency.band(), lexical.band(), grammar.band(), pronunciation.band()));
        SpeechMetrics metrics = speechMetrics(root.path("speech_metrics"), sources);
        List<AnswerUpgrade> upgrades = answerUpgrades(root.path("answer_upgrades"));
        return new Result(provider, model, overall, fluency, lexical, grammar, pronunciation,
                metrics, upgrades, strings(root.path("strengths")), strings(root.path("improvements")), cleaned);
    }

    private SpeechMetrics speechMetrics(JsonNode node, List<SpeakingEvaluationService.ResponseSource> sources) {
        Integer pauses = node != null && node.path("pause_count").canConvertToInt()
                ? node.path("pause_count").asInt() : null;
        if (pauses != null && pauses < 0) pauses = null;
        List<PronunciationIssue> issues = new ArrayList<>();
        JsonNode issuesNode = node == null ? null : node.path("pronunciation_issues");
        if (issuesNode != null && issuesNode.isArray()) {
            issuesNode.forEach(item -> {
                String word = item.path("word").asText("").trim();
                if (item.isObject() && !word.isBlank()) {
                    issues.add(new PronunciationIssue(
                            word,
                            stripIpaSlashes(item.path("ipa").asText("").trim()),
                            item.path("issue").asText("").trim()
                    ));
                }
            });
        }
        return new SpeechMetrics(wordsPerMinute(sources), pauses, pauses != null,
                fillerWords(sources), List.copyOf(issues));
    }

    private Integer wordsPerMinute(List<SpeakingEvaluationService.ResponseSource> sources) {
        long durationMs = 0;
        int words = 0;
        for (var source : sources) {
            if (source.durationMs() == null || source.durationMs() <= 0 || source.transcript() == null) continue;
            durationMs += source.durationMs();
            words += wordCount(source.transcript());
        }
        return durationMs == 0 ? null : (int) Math.round(words * 60_000d / durationMs);
    }

    private Map<String, Integer> fillerWords(List<SpeakingEvaluationService.ResponseSource> sources) {
        String transcript = sources.stream().map(SpeakingEvaluationService.ResponseSource::transcript)
                .filter(value -> value != null && !value.isBlank()).reduce("", (a, b) -> a + " " + b);
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String filler : FILLERS) {
            var matcher = Pattern.compile("(?i)(?<![\\p{L}'])" + Pattern.quote(filler) + "(?![\\p{L}'])")
                    .matcher(transcript);
            int count = 0;
            while (matcher.find()) count++;
            if (count > 0) result.put(filler, count);
        }
        return Map.copyOf(result);
    }

    private int wordCount(String transcript) {
        var matcher = Pattern.compile("[\\p{L}]+(?:['’][\\p{L}]+)?").matcher(transcript);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private String stripIpaSlashes(String value) {
        return value.length() >= 2 && value.startsWith("/") && value.endsWith("/")
                ? value.substring(1, value.length() - 1).trim() : value;
    }

    private List<AnswerUpgrade> answerUpgrades(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<AnswerUpgrade> result = new ArrayList<>();
        node.forEach(item -> {
            String original = item.path("original_text").asText("").trim();
            String upgraded = item.path("upgraded_text").asText("").trim();
            if (item.isObject() && !original.isBlank() && !upgraded.isBlank()) {
                result.add(new AnswerUpgrade(
                        item.path("question_key").asText("").trim(),
                        original,
                        upgraded,
                        strings(item.path("collocations")),
                        item.path("explanation").asText("").trim()
                ));
            }
        });
        return result;
    }

    private Criterion criterion(JsonNode parent, String key) {
        JsonNode value = parent.path(key);
        if (!value.path("band").isNumber() || value.path("feedback").asText().isBlank()) {
            throw new IllegalArgumentException("AI response is missing criterion " + key);
        }
        return new Criterion(half(value.path("band").decimalValue()), value.path("feedback").asText().trim());
    }

    private BigDecimal half(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(9)) > 0) {
            throw new IllegalArgumentException("Speaking band must be between 0 and 9");
        }
        return BigDecimal.valueOf(Math.round(value.doubleValue() * 2d) / 2d).setScale(1, RoundingMode.HALF_UP);
    }

    private List<String> strings(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        node.forEach(item -> { if (item.isTextual() && !item.asText().isBlank()) result.add(item.asText().trim()); });
        return result;
    }

    public record Criterion(BigDecimal band, String feedback) {}
    public record Result(String provider, String model, BigDecimal overall, Criterion fluency, Criterion lexical,
                         Criterion grammar, Criterion pronunciation, SpeechMetrics speechMetrics,
                         List<AnswerUpgrade> answerUpgrades, List<String> strengths,
                         List<String> improvements, String rawResponse) {}
}
