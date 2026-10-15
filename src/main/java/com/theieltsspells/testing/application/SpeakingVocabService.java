package com.theieltsspells.testing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.ai.AiGenerationRequest;
import com.theieltsspells.shared.ai.AiProviderRouter;
import com.theieltsspells.shared.ai.AiTaskType;
import com.theieltsspells.testing.application.dto.SpeakingVocabLookupRequest;
import com.theieltsspells.testing.application.dto.SpeakingVocabLookupResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakingVocabService {

    private final AiProviderRouter router;
    private final ObjectMapper objectMapper;

    public SpeakingVocabLookupResponse lookup(SpeakingVocabLookupRequest request) {
        String cleanQuery = request.query().trim();
        String direction = "VI_EN".equalsIgnoreCase(request.direction()) ? "VI_EN" : "EN_VI";

        String prompt = buildPrompt(cleanQuery, direction);
        var aiRequest = new AiGenerationRequest(
                systemPrompt(direction),
                prompt,
                1500,
                0.2,
                "application/json",
                true,
                router.timeoutFor(AiTaskType.CHAT)
        );

        try {
            return router.execute(AiTaskType.CHAT, aiRequest, result -> decode(cleanQuery, direction, result.content()));
        } catch (Exception e) {
            log.warn("Speaking vocab lookup failed for '{}' ({}): {}", cleanQuery, direction, e.getMessage());
            return fallbackResponse(cleanQuery, direction);
        }
    }

    private String systemPrompt(String direction) {
        if ("VI_EN".equals(direction)) {
            return """
                    You are an expert bilingual IELTS vocabulary tutor.
                    Translate Vietnamese words/phrases into natural, high-band (IELTS 6.5 - 8.0) English vocabulary or collocations suitable for IELTS Speaking.
                    Return JSON ONLY with this exact schema:
                    {
                      "mainTranslation": "natural English equivalent",
                      "phonetic": "/.../",
                      "partOfSpeech": "verb / noun / adjective / idiom",
                      "synonyms": ["phrase 1", "phrase 2", "phrase 3"],
                      "examples": [
                        {"english": "IELTS Speaking sample sentence...", "vietnamese": "Dịch nghĩa câu ví dụ..."}
                      ]
                    }
                    """;
        }
        return """
                You are an expert bilingual IELTS vocabulary tutor.
                Translate English words/phrases into accurate Vietnamese for IELTS learners, providing clear definitions, pronunciation, collocations, and IELTS Speaking example sentences.
                Return JSON ONLY with this exact schema:
                {
                  "mainTranslation": "Dịch nghĩa tiếng Việt chính xác",
                  "phonetic": "/.../",
                  "partOfSpeech": "verb / noun / adjective / idiom",
                  "synonyms": ["synonym 1", "synonym 2", "synonym 3"],
                  "examples": [
                    {"english": "IELTS Speaking sample sentence...", "vietnamese": "Dịch nghĩa câu ví dụ..."}
                  ]
                }
                """;
    }

    private String buildPrompt(String query, String direction) {
        return "Query: \"" + query + "\" (Direction: " + direction + "). Provide the IELTS Speaking vocabulary lookup JSON.";
    }

    private SpeakingVocabLookupResponse decode(String query, String direction, String content) throws Exception {
        String cleaned = content.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        JsonNode root = objectMapper.readTree(cleaned);

        String mainTranslation = root.path("mainTranslation").asText(query);
        String phonetic = root.path("phonetic").asText("");
        String partOfSpeech = root.path("partOfSpeech").asText("");

        List<String> synonyms = new ArrayList<>();
        if (root.path("synonyms").isArray()) {
            root.path("synonyms").forEach(node -> {
                if (node.isTextual() && !node.asText().isBlank()) {
                    synonyms.add(node.asText().trim());
                }
            });
        }

        List<SpeakingVocabLookupResponse.ExampleItem> examples = new ArrayList<>();
        if (root.path("examples").isArray()) {
            root.path("examples").forEach(node -> {
                String en = node.path("english").asText("");
                String vi = node.path("vietnamese").asText("");
                if (!en.isBlank()) {
                    examples.add(new SpeakingVocabLookupResponse.ExampleItem(en, vi));
                }
            });
        }

        return new SpeakingVocabLookupResponse(
                query,
                direction,
                mainTranslation,
                phonetic,
                partOfSpeech,
                synonyms,
                examples
        );
    }

    private SpeakingVocabLookupResponse fallbackResponse(String query, String direction) {
        return new SpeakingVocabLookupResponse(
                query,
                direction,
                query,
                "",
                "vocabulary",
                List.of(),
                List.of(new SpeakingVocabLookupResponse.ExampleItem(
                        "I often use this phrase when discussing familiar topics in IELTS Speaking.",
                        "Tôi thường sử dụng cụm từ này khi thảo luận về các chủ đề quen thuộc."
                ))
        );
    }
}
