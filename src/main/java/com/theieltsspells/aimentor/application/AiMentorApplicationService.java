package com.theieltsspells.aimentor.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.aimentor.application.dto.AiMentorChatRequest;
import com.theieltsspells.aimentor.application.dto.AiMentorChatResponse;
import com.theieltsspells.shared.ai.AiGenerationRequest;
import com.theieltsspells.shared.ai.AiProviderRouter;
import com.theieltsspells.shared.ai.AiTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiMentorApplicationService {

    private static final Map<String, String> MENTOR_PERSONAS = Map.of(
            "DRAGON", "Ignis the Dragon (Male energetic companion & IELTS/Arena coach: motivating, playful yet sharp)",
            "OWL", "Spellie the Owl (Female wise companion & academic mentor: warm, clear, methodical)",
            "nova_us", "Spellie the Owl (Female warm companion)",
            "orion_us", "Ignis the Dragon (Male energetic companion)",
            "aria_gb", "Spellie the Owl (Female academic companion)",
            "oliver_gb", "Ignis the Dragon (Male arena tactician)"
    );

    private final AiProviderRouter aiProviderRouter;
    private final ObjectMapper objectMapper;

    public AiMentorChatResponse chat(AiMentorChatRequest req) {
        String mentorId = req.mentorId() != null ? req.mentorId().toUpperCase(Locale.ROOT) : "OWL";
        String persona = MENTOR_PERSONAS.getOrDefault(mentorId, MENTOR_PERSONAS.get("OWL"));
        String accent = "UK".equalsIgnoreCase(req.accent()) ? "British English (UK)" : "American English (US)";
        String cefr = (req.cefrLevel() == null || req.cefrLevel().isBlank()) ? "B1" : req.cefrLevel().toUpperCase(Locale.ROOT);
        String mode = (req.mode() == null || req.mode().isBlank()) ? "RECOMMEND" : req.mode().toUpperCase(Locale.ROOT);
        String weakSummary = (req.weakWords() == null || req.weakWords().isEmpty())
                ? "none recorded yet"
                : req.weakWords().stream().limit(8).collect(Collectors.joining(", "));

        String systemPrompt = """
                You are a friendly English learning Companion Mascot inside "The IELTS Spells" app.
                Persona: %s. Target dialect/spelling: %s.
                Learner CEFR level: %s. Goal: %s. Active Mode: %s.
                Learner weak/saved words: %s.
                Story context (if any): %s - %s.

                Return ONLY valid JSON with this exact schema:
                {
                  "enReply": "concise, natural English response matched to learner CEFR level (2-4 sentences)",
                  "viReply": "100%% natural, idiomatic Vietnamese using ONLY Latin script + Vietnamese diacritics (NO Chinese/Japanese/Korean characters!)",
                  "suggestedChips": ["short follow-up chip 1 in Vietnamese or English", "short follow-up chip 2", "short follow-up chip 3"],
                  "extractedVocab": [{"word": "...", "pos": "noun|verb|adj|phrase", "meaningVi": "...", "cefr": "%s"}],
                  "microQuest": null or {"question": "short multiple-choice trap question testing what we just explained", "options": ["A", "B", "C"], "correctIndex": 0, "explanationVi": "..."},
                  "actionCard": null or {"actionType": "SMART_REVIEW|STORY_LIBRARY|SPEED_VOCAB|COLLOCATION|GRAMMAR|PVP_ARENA", "title": "...", "subtitle": "...", "targetCefr": "%s"}
                }
                Strict Rules:
                - LANGUAGE INTEGRITY: viReply, explanationVi, meaningVi MUST contain ZERO Chinese/CJK/Japanese/Korean characters and NO stray unrequested English words stitched awkwardly into Vietnamese prose. Translate naturally as an encouraging native Vietnamese teacher (for example, "Tưởng tượng chúng ta đang ở buổi phỏng vấn..." instead of awkward word-by-word translations). Never say "tôi là AI" or "trí tuệ nhân tạo".
                - Stay STRICTLY within Active Mode (%s):
                  * SHADOWING: Give ONE vivid, natural spoken English sentence (do NOT push reading stories; encourage repeating aloud with the in-chat mic).
                  * ROLEPLAY: Continue an immersive 2-way dialogue scenario in English, gently polish 1 phrase if needed, and ask a natural next conversational turn.
                  * VOCAB: Explain nuances, collocations, and provide a MicroQuest.
                  * GRAMMAR: Spot grammar mistakes, explain concisely, and provide a MicroQuest.
                  * RECOMMEND: Suggest concrete next practice activities matched to learner profile.
                  * PVP_WARMUP: Rapid 60-second arena warm-up coaching.
                - Only include actionCard when genuinely helpful; NEVER suggest QUICK_SPEAKING inside SHADOWING or ROLEPLAY turns.
                """.formatted(
                persona,
                accent,
                cefr,
                req.learningGoal() != null ? req.learningGoal() : "IELTS",
                mode,
                weakSummary,
                req.storyTitle() != null ? req.storyTitle() : "none",
                req.storySummary() != null ? req.storySummary() : "",
                cefr, cefr, mode
        );

        StringBuilder userPrompt = new StringBuilder();
        if (req.history() != null && !req.history().isEmpty()) {
            userPrompt.append("Recent conversation:\n");
            for (AiMentorChatRequest.HistoryTurn turn : req.history().stream().limit(6).toList()) {
                userPrompt.append(turn.role()).append(": ").append(turn.text()).append('\n');
            }
            userPrompt.append('\n');
        }
        userPrompt.append("Learner message: ").append(req.userMessage().trim());

        try {
            AiGenerationRequest aiReq = new AiGenerationRequest(
                    systemPrompt,
                    userPrompt.toString(),
                    1100,
                    0.45,
                    "application/json",
                    true,
                    Duration.ofSeconds(18)
            );
            return aiProviderRouter.execute(AiTaskType.CHAT, aiReq, result -> {
                JsonNode root = objectMapper.readTree(stripJsonFences(result.content()));
                List<String> chips = root.has("suggestedChips")
                        ? objectMapper.convertValue(root.get("suggestedChips"), new TypeReference<>() {})
                        : List.of();
                List<AiMentorChatResponse.VocabTip> vocab = root.has("extractedVocab")
                        ? objectMapper.convertValue(root.get("extractedVocab"), new TypeReference<>() {})
                        : List.of();
                AiMentorChatResponse.MicroQuest quest = root.hasNonNull("microQuest")
                        ? sanitizeQuest(objectMapper.treeToValue(root.get("microQuest"), AiMentorChatResponse.MicroQuest.class))
                        : null;
                AiMentorChatResponse.ActionCard card = root.hasNonNull("actionCard")
                        ? objectMapper.treeToValue(root.get("actionCard"), AiMentorChatResponse.ActionCard.class)
                        : null;
                String cleanVi = sanitizeVietnamese(
                        root.path("viReply").asText("Hãy cùng luyện tập từng bước nhé!"),
                        "Hãy cùng luyện tập câu này theo đúng ngữ điệu nhé!");
                return new AiMentorChatResponse(
                        root.path("enReply").asText("Let's keep practicing step by step!"),
                        cleanVi,
                        chips,
                        vocab,
                        quest,
                        card,
                        "AI_" + result.provider().name()
                );
            });
        } catch (Exception ex) {
            log.debug("AI Mentor fallback activated: {}", ex.getMessage());
            return buildSmartFallback(req, cefr, mode, weakSummary);
        }
    }

    private AiMentorChatResponse buildSmartFallback(
            AiMentorChatRequest req,
            String cefr,
            String mode,
            String weakSummary
    ) {
        if ("SHADOWING".equals(mode)) {
            return new AiMentorChatResponse(
                    "Listen carefully and shadow this " + cefr + " sentence: \"Consistency beats intensity when building a lasting English habit.\"",
                    "Nghe kỹ câu mẫu chuẩn " + cefr + " và đọc nhại lại đúng nhịp điệu: \"Sự kiên trì luôn thắng cường độ ngắn hạn khi xây dựng thói quen tiếng Anh.\"",
                    List.of("Cho mình câu dễ hơn", "Câu giao tiếp đi làm", "Giải thích từ consistency"),
                    List.of(new AiMentorChatResponse.VocabTip("consistency", "noun", "sự kiên trì, nhất quán", cefr)),
                    null,
                    new AiMentorChatResponse.ActionCard("QUICK_SPEAKING", "Chấm phát âm ngay", "Chuyển sang Quick Speaking để kiểm tra độ trôi chảy", cefr),
                    "SMART_FALLBACK"
            );
        }
        if ("GRAMMAR".equals(mode)) {
            return new AiMentorChatResponse(
                    "Watch out for tense agreement! With finished past time markers like 'last year', always use Past Simple ('went'), not Present Perfect ('have went').",
                    "Lưu ý hòa hợp thì nhé! Với mốc thời gian quá khứ đã kết thúc như 'last year', luôn dùng Quá khứ đơn ('went') chứ không dùng 'have went'.",
                    List.of("'since' và 'for' khác gì?", "Thử thêm 1 câu bẫy", "Lưu mẹo này"),
                    List.of(),
                    new AiMentorChatResponse.MicroQuest(
                            "Chọn câu đúng ngữ pháp:",
                            List.of("I went to Japan last year.", "I have went to Japan last year.", "I have go to Japan last year."),
                            0,
                            "'Last year' là mốc quá khứ xác định nên chia quá khứ đơn 'went'."
                    ),
                    new AiMentorChatResponse.ActionCard("GRAMMAR", "Thử thách Grammar Detective", "Săn lỗi sai theo trình độ " + cefr, cefr),
                    "SMART_FALLBACK"
            );
        }
        if ("VOCAB".equals(mode)) {
            return new AiMentorChatResponse(
                    "Great question! 'Mitigate' (verb) means to make something harmful less severe — a staple Band 7.5+ verb in IELTS essays.",
                    "Câu hỏi rất hay! 'Mitigate' (động từ) nghĩa là làm giảm nhẹ mức độ nghiêm trọng — từ vựng rất đắt giá cho IELTS Band 7.5+.",
                    List.of("affect vs effect", "take something for granted", "Ôn từ yếu của tôi"),
                    List.of(new AiMentorChatResponse.VocabTip("mitigate", "verb", "làm dịu bớt, giảm nhẹ", "C1")),
                    new AiMentorChatResponse.MicroQuest(
                            "Từ nào đồng nghĩa gần nhất với 'mitigate'?",
                            List.of("alleviate", "aggravate", "ignore"),
                            0,
                            "'Alleviate' = làm giảm nhẹ (tương đồng 'mitigate'); 'aggravate' là làm trầm trọng thêm."
                    ),
                    null,
                    "SMART_FALLBACK"
            );
        }
        if ("PVP_WARMUP".equals(mode)) {
            return new AiMentorChatResponse(
                    "Before stepping into the 1v1 Arena at level " + cefr + ", sharpen your reflexes on your saved weak words (" + weakSummary + ")!",
                    "Trước khi bước vào Đấu trường 1v1 ở trình độ " + cefr + ", hãy khởi động nhanh phản xạ với các từ bạn hay nhầm nhé!",
                    List.of("Ôn 1 câu bẫy ngay", "Vào đấu trường 1v1"),
                    List.of(),
                    null,
                    new AiMentorChatResponse.ActionCard("SMART_REVIEW", "Khởi động với Phòng Ôn Từ Yếu", "Ôn trúng đích trước khi đấu Rank PvP", cefr),
                    "SMART_FALLBACK"
            );
        }
        return new AiMentorChatResponse(
                "Based on your " + cefr + " profile, start with one short bilingual story to absorb natural phrasing, then lock in weak words (" + weakSummary + ").",
                "Dựa trên trình độ " + cefr + " của bạn, hãy đọc 1 truyện song ngữ ngắn để ngấm ngữ cảnh, sau đó củng cố các từ hay sai (" + weakSummary + ").",
                List.of("Mình có 5 phút, học gì?", "Cho mình câu Shadowing", "Kiểm tra ngữ pháp nhanh"),
                List.of(),
                null,
                new AiMentorChatResponse.ActionCard("STORY_LIBRARY", "Mở Truyện Song Ngữ " + cefr, "Đọc 2-3 phút & chạm tra từ trực tiếp", cefr),
                "SMART_FALLBACK"
        );
    }

    private static AiMentorChatResponse.MicroQuest sanitizeQuest(AiMentorChatResponse.MicroQuest q) {
        if (q == null) return null;
        return new AiMentorChatResponse.MicroQuest(
                sanitizeVietnamese(q.question(), "Chọn đáp án đúng nhất:"),
                q.options(),
                q.correctIndex(),
                sanitizeVietnamese(q.explanationVi(), "Ghi nhớ cấu trúc này khi giao tiếp nhé!")
        );
    }

    private static String sanitizeVietnamese(String text, String fallback) {
        if (text == null || text.isBlank()) return fallback;
        String stripped = text.replaceAll("[\\u4E00-\\u9FFF\\u3040-\\u30FF\\uAC00-\\uD7AF]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return stripped.isEmpty() ? fallback : stripped;
    }

    private static String stripJsonFences(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }
        return s.trim();
    }
}
