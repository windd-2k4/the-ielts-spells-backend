package com.theieltsspells.testing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.ai.AiAudioInput;
import com.theieltsspells.shared.ai.AiGenerationRequest;
import com.theieltsspells.shared.ai.AiProviderRouter;
import com.theieltsspells.shared.ai.AiTaskType;
import com.theieltsspells.shared.SpeakingBandScorePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SpeakingAiEvaluator {
    public static final String RUBRIC_VERSION = "ielts-speaking-public-v1";
    public static final String PROMPT_VERSION = "speaking-evaluator-v2";
    private final AiProviderRouter router;
    private final ObjectMapper objectMapper;

    public Result evaluate(List<SpeakingEvaluationService.ResponseSource> sources) {
        List<AiAudioInput> audio = sources.stream().map(SpeakingEvaluationService.ResponseSource::audioUrl)
                .filter(value -> value != null && !value.isBlank()).map(AiAudioInput::fromDataUrl).toList();
        if (audio.isEmpty()) throw new IllegalArgumentException("Bài Speaking chưa có file ghi âm để chấm Pronunciation");
        long encodedBytes = audio.stream().mapToLong(value -> value.base64Data().length()).sum();
        if (encodedBytes > 24_000_000L) {
            throw new IllegalArgumentException("Tổng dung lượng ghi âm vượt giới hạn xử lý AI; giáo viên vẫn có thể chấm thủ công");
        }
        String transcript = sources.stream().filter(value -> value.transcript() != null)
                .map(value -> value.questionKey() + ": " + value.transcript())
                .reduce((left, right) -> left + "\n\n" + right).orElse("Không có transcript");
        var request = new AiGenerationRequest(systemPrompt(), "TRANSCRIPT THEO TỪNG CÂU:\n" + transcript,
                3200, 0.1, "application/json", true, router.timeoutFor(AiTaskType.CHAT), List.of(), audio);
        return router.execute(AiTaskType.CHAT, request,
                value -> decode(value.provider().name(), value.model(), value.content()));
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
                Mỗi nhận xét phải nêu bằng chứng quan sát được, bằng tiếng Việt. Chỉ trả JSON:
                {"criteria":{"fluency_coherence":{"band":6.0,"feedback":"..."},
                "lexical_resource":{"band":6.0,"feedback":"..."},
                "grammatical_range_accuracy":{"band":6.0,"feedback":"..."},
                "pronunciation":{"band":6.0,"feedback":"..."}},
                "strengths":["..."],"improvements":["..."]}
                """;
    }

    private Result decode(String provider, String model, String content) throws Exception {
        String cleaned = content.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        JsonNode root = objectMapper.readTree(cleaned);
        JsonNode criteria = root.path("criteria");
        Criterion fluency = criterion(criteria, "fluency_coherence");
        Criterion lexical = criterion(criteria, "lexical_resource");
        Criterion grammar = criterion(criteria, "grammatical_range_accuracy");
        Criterion pronunciation = criterion(criteria, "pronunciation");
        BigDecimal overall = SpeakingBandScorePolicy.overall(List.of(
                fluency.band(), lexical.band(), grammar.band(), pronunciation.band()));
        return new Result(provider, model, overall, fluency, lexical, grammar, pronunciation,
                strings(root.path("strengths")), strings(root.path("improvements")), cleaned);
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
                         Criterion grammar, Criterion pronunciation, List<String> strengths,
                         List<String> improvements, String rawResponse) {}
}
