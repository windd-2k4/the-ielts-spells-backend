package com.theieltsspells.dictionary.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.dictionary.application.dto.DictionaryLookupResponse;
import com.theieltsspells.dictionary.application.dto.DictionarySuggestItemResponse;
import com.theieltsspells.dictionary.application.dto.LookupHistoryItemResponse;
import com.theieltsspells.dictionary.application.dto.SaveDictionaryWordRequest;
import com.theieltsspells.dictionary.application.dto.SavedWordResponse;
import com.theieltsspells.dictionary.application.dto.TranslateTextRequest;
import com.theieltsspells.dictionary.application.dto.TranslateTextResponse;
import com.theieltsspells.dictionary.domain.DictionaryDirection;
import com.theieltsspells.dictionary.infrastructure.DictionaryProperties;
import com.theieltsspells.dictionary.infrastructure.LibreTranslateClient;
import com.theieltsspells.shared.ai.AiGenerationRequest;
import com.theieltsspells.shared.ai.AiProviderRouter;
import com.theieltsspells.shared.ai.AiTaskType;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DictionaryApplicationService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final LibreTranslateClient libreTranslateClient;
    private final AiProviderRouter aiProviderRouter;
    private final DictionaryProperties properties;

    @Transactional(readOnly = true)
    public List<DictionarySuggestItemResponse> suggest(String query, DictionaryDirection requestedDirection, int limit) {
        String clean = query == null ? "" : query.trim();
        if (clean.isEmpty()) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 25));
        DictionaryDirection resolved = DictionaryDirection.resolve(clean, requestedDirection);
        String lowerPrefix = clean.toLowerCase(Locale.ROOT) + "%";
        String normPrefix = DictionaryDirection.normalizeForSearch(clean) + "%";
        String exactLower = clean.toLowerCase(Locale.ROOT);

        try {
            return jdbc.query("""
                select headword, direction, ipa, cefr_level, part_of_speech, summary
                from public.dictionary_entries
                where direction = ?
                  and (headword_lower like ? or normalized_headword like ?)
                order by
                  case when headword_lower = ? then 0 else 1 end,
                  char_length(headword),
                  headword_lower
                limit ?
                """,
                (rs, rowNum) -> new DictionarySuggestItemResponse(
                        rs.getString("headword"),
                        rs.getString("direction"),
                        rs.getString("ipa"),
                        rs.getString("cefr_level"),
                        rs.getString("part_of_speech"),
                        rs.getString("summary")
                ),
                resolved.name(), lowerPrefix, normPrefix, exactLower, safeLimit);
        } catch (Exception ex) {
            log.debug("Dictionary suggest fallback (table unavailable or empty): {}", ex.getMessage());
            return List.of();
        }
    }

    @Transactional
    public DictionaryLookupResponse lookup(UUID userId, String word, DictionaryDirection requestedDirection) {
        String clean = word == null ? "" : word.trim();
        if (clean.isEmpty()) {
            throw new ResourceNotFoundException("Từ cần tra không được để trống");
        }
        DictionaryDirection resolved = DictionaryDirection.resolve(clean, requestedDirection);
        String lower = clean.toLowerCase(Locale.ROOT);
        String norm = DictionaryDirection.normalizeForSearch(clean);

        boolean saved = isSavedInNotebook(userId, clean);

        List<DictionaryLookupResponse> rows = List.of();
        try {
            rows = jdbc.query("""
                select headword, direction, ipa, cefr_level, part_of_speech, summary, payload::text as payload_text, source
                from public.dictionary_entries
                where direction = ?
                  and (headword_lower = ? or normalized_headword = ?)
                order by case when headword_lower = ? then 0 else 1 end, id
                limit 1
                """,
                (rs, rowNum) -> mapLookupRow(
                        rs.getString("headword"),
                        rs.getString("direction"),
                        rs.getString("ipa"),
                        rs.getString("cefr_level"),
                        rs.getString("part_of_speech"),
                        rs.getString("summary"),
                        rs.getString("payload_text"),
                        rs.getString("source"),
                        saved
                ),
                resolved.name(), lower, norm, lower);
        } catch (Exception ex) {
            log.warn("Dictionary primary lookup skipped ({}): {}", clean, ex.getMessage());
        }

        if (!rows.isEmpty()) {
            DictionaryLookupResponse found = rows.getFirst();
            recordHistory(userId, found.headword(), found.direction(), "WORD", found.summary());
            return found;
        }

        // Cross-direction fallback if user selected wrong toggle
        DictionaryDirection other = resolved == DictionaryDirection.EN_VI
                ? DictionaryDirection.VI_EN
                : DictionaryDirection.EN_VI;
        List<DictionaryLookupResponse> crossRows = List.of();
        try {
            crossRows = jdbc.query("""
                select headword, direction, ipa, cefr_level, part_of_speech, summary, payload::text as payload_text, source
                from public.dictionary_entries
                where direction = ?
                  and (headword_lower = ? or normalized_headword = ?)
                limit 1
                """,
                (rs, rowNum) -> mapLookupRow(
                        rs.getString("headword"),
                        rs.getString("direction"),
                        rs.getString("ipa"),
                        rs.getString("cefr_level"),
                        rs.getString("part_of_speech"),
                        rs.getString("summary"),
                        rs.getString("payload_text"),
                        rs.getString("source"),
                        saved
                ),
                other.name(), lower, norm);
        } catch (Exception ex) {
            log.warn("Dictionary cross lookup skipped ({}): {}", clean, ex.getMessage());
        }

        if (!crossRows.isEmpty()) {
            DictionaryLookupResponse found = crossRows.getFirst();
            recordHistory(userId, found.headword(), found.direction(), "WORD", found.summary());
            return found;
        }

        // Smart online fallback via AI router when word/phrase is absent from base dictionary
        Optional<DictionaryLookupResponse> aiFallback = lookupViaAi(clean, resolved, saved);
        if (aiFallback.isPresent()) {
            DictionaryLookupResponse found = aiFallback.get();
            recordHistory(userId, found.headword(), found.direction(), "WORD", found.summary());
            return found;
        }

        throw new ResourceNotFoundException("Không tìm thấy từ '" + clean + "' trong từ điển");
    }

    @Transactional
    public TranslateTextResponse translate(UUID userId, TranslateTextRequest request) {
        String text = request.text().trim();
        DictionaryDirection resolvedDir = resolveTranslateDirection(text, request.sourceLang(), request.targetLang());
        String sourceLang = resolvedDir == DictionaryDirection.EN_VI ? "en" : "vi";
        String targetLang = resolvedDir == DictionaryDirection.EN_VI ? "vi" : "en";

        Optional<String> libreResult = request.includeIeltsTip()
                ? Optional.empty()
                : libreTranslateClient.translate(text, sourceLang, targetLang);

        if (libreResult.isPresent()) {
            String translated = libreResult.get();
            recordHistory(userId, truncate(text, 200), resolvedDir.name(), "TRANSLATE", truncate(translated, 240));
            return new TranslateTextResponse(
                    text,
                    translated,
                    sourceLang,
                    targetLang,
                    "LIBRETRANSLATE",
                    null,
                    List.of()
            );
        }

        // Fallback to configured AI router (Gemini/Nvidia) or LibreTranslate retry
        Optional<TranslateTextResponse> aiTranslated = translateViaAi(text, sourceLang, targetLang, request.includeIeltsTip());
        if (aiTranslated.isPresent()) {
            TranslateTextResponse response = aiTranslated.get();
            recordHistory(userId, truncate(text, 200), resolvedDir.name(), "TRANSLATE", truncate(response.translatedText(), 240));
            return response;
        }

        // Last resort retry LibreTranslate if includeIeltsTip was true and AI unavailable
        Optional<String> retryLibre = libreTranslateClient.translate(text, sourceLang, targetLang);
        if (retryLibre.isPresent()) {
            String translated = retryLibre.get();
            recordHistory(userId, truncate(text, 200), resolvedDir.name(), "TRANSLATE", truncate(translated, 240));
            return new TranslateTextResponse(text, translated, sourceLang, targetLang, "LIBRETRANSLATE", null, List.of());
        }

        throw new IllegalStateException("Dịch vụ dịch online hiện chưa sẵn sàng. Hãy bật LibreTranslate hoặc cấu hình GEMINI_API_KEY.");
    }

    @Transactional
    public SavedWordResponse saveToNotebook(UUID studentId, SaveDictionaryWordRequest request) {
        ensureStudentProfileRow(studentId);
        UUID topicId = request.topicId() == null ? ensureDefaultDictionaryTopic(studentId) : request.topicId();
        String term = request.headword().trim();
        String note = request.ipa() != null && !request.ipa().isBlank()
                ? "IPA: " + request.ipa().trim()
                : null;
        return jdbc.queryForObject("""
                insert into public.student_vocabulary
                  (student_id, topic_id, term, meaning, example_sentence, part_of_speech, note, source_label,
                   mastery_level, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, 'Từ điển The IELTS Spells', 0, now(), now())
                on conflict (student_id, topic_id, (lower(term))) do update
                set meaning = coalesce(nullif(excluded.meaning, ''), student_vocabulary.meaning),
                    example_sentence = coalesce(nullif(excluded.example_sentence, ''), student_vocabulary.example_sentence),
                    part_of_speech = coalesce(nullif(excluded.part_of_speech, ''), student_vocabulary.part_of_speech),
                    note = coalesce(nullif(excluded.note, ''), student_vocabulary.note),
                    updated_at = now()
                returning id, topic_id, term, meaning
                """,
                (rs, rowNum) -> new SavedWordResponse(
                        rs.getObject("id", UUID.class),
                        rs.getObject("topic_id", UUID.class),
                        rs.getString("term"),
                        rs.getString("meaning"),
                        "NOT_LEARNED"
                ),
                studentId, topicId, term, request.meaning(), request.exampleSentence(), request.partOfSpeech(), note);
    }

    private void ensureStudentProfileRow(UUID studentId) {
        String code = "HV-" + studentId.toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        jdbc.update("""
                insert into public.profiles (id, full_name, is_active, created_at, updated_at)
                values (?, 'Học viên', true, now(), now())
                on conflict (id) do nothing
                """, studentId);
        jdbc.update("""
                insert into public.student_profiles (user_id, student_code)
                values (?, ?)
                on conflict (user_id) do nothing
                """, studentId, code);
    }

    private UUID ensureDefaultDictionaryTopic(UUID studentId) {
        List<UUID> existing = jdbc.query("""
                select id from public.student_vocabulary_topics
                where student_id = ? and lower(name) = lower('Từ điển')
                limit 1
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), studentId);
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        jdbc.update("""
                insert into public.student_vocabulary_topics (student_id, name)
                values (?, 'Từ điển')
                on conflict (student_id, (lower(name))) do nothing
                """, studentId);
        return jdbc.queryForObject("""
                select id from public.student_vocabulary_topics
                where student_id = ? and lower(name) = lower('Từ điển')
                limit 1
                """, UUID.class, studentId);
    }

    @Transactional(readOnly = true)
    public List<LookupHistoryItemResponse> history(UUID userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return jdbc.query("""
                select id, query_text, direction, entry_type, summary, created_at
                from public.student_lookup_history
                where student_id = ?
                order by created_at desc
                limit ?
                """,
                (rs, rowNum) -> new LookupHistoryItemResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("query_text"),
                        rs.getString("direction"),
                        rs.getString("entry_type"),
                        rs.getString("summary"),
                        rs.getObject("created_at", OffsetDateTime.class)
                ),
                userId, safeLimit);
    }

    public Resource getOfflineSqliteBundle() {
        Path bundlePath = Path.of(properties.getSqliteBundlePath());
        if (!Files.exists(bundlePath)) {
            throw new ResourceNotFoundException("Gói từ điển offline chưa được tạo trên máy chủ");
        }
        return new FileSystemResource(bundlePath);
    }

    public Map<String, Object> getOfflineBundleMeta() {
        Path bundlePath = Path.of(properties.getSqliteBundlePath());
        boolean available = Files.exists(bundlePath);
        long sizeBytes = 0;
        if (available) {
            try {
                sizeBytes = Files.size(bundlePath);
            } catch (Exception ignored) {}
        }
        return Map.of(
                "available", available,
                "fileName", "ielts_dict.sqlite",
                "sizeBytes", sizeBytes,
                "version", "2026.11.1"
        );
    }

    private DictionaryLookupResponse mapLookupRow(
            String headword,
            String direction,
            String ipa,
            String cefrLevel,
            String partOfSpeech,
            String summary,
            String payloadText,
            String source,
            boolean savedInNotebook
    ) {
        try {
            JsonNode payload = payloadText != null ? objectMapper.readTree(payloadText) : objectMapper.createObjectNode();
            List<DictionaryLookupResponse.MeaningGroup> meanings = payload.has("meanings")
                    ? objectMapper.convertValue(payload.get("meanings"), new TypeReference<>() {})
                    : List.of();
            List<DictionaryLookupResponse.IeltsDefinition> ieltsDefs = payload.has("ieltsDefinitions")
                    ? objectMapper.convertValue(payload.get("ieltsDefinitions"), new TypeReference<>() {})
                    : List.of();
            List<DictionaryLookupResponse.WordFamilyItem> wordFamily = payload.has("wordFamily")
                    ? objectMapper.convertValue(payload.get("wordFamily"), new TypeReference<>() {})
                    : List.of();

            return new DictionaryLookupResponse(
                    headword,
                    direction,
                    ipa,
                    payload.path("phoneticUk").asText(null),
                    payload.path("phoneticUs").asText(null),
                    payload.path("audioUrl").asText(null),
                    cefrLevel,
                    partOfSpeech,
                    summary,
                    meanings,
                    ieltsDefs,
                    wordFamily,
                    payload.path("etymology").asText(null),
                    source,
                    savedInNotebook
            );
        } catch (Exception ex) {
            log.warn("Failed to parse dictionary payload for {}: {}", headword, ex.getMessage());
            return new DictionaryLookupResponse(
                    headword, direction, ipa, null, null, null,
                    cefrLevel, partOfSpeech, summary,
                    List.of(), List.of(), List.of(), null, source, savedInNotebook
            );
        }
    }

    private Optional<DictionaryLookupResponse> lookupViaAi(String word, DictionaryDirection direction, boolean saved) {
        String systemPrompt = """
                You are an IELTS English-Vietnamese bilingual dictionary engine.
                Return ONLY valid JSON with this exact shape:
                {
                  "headword": "...",
                  "ipa": "/.../",
                  "cefrLevel": "B2",
                  "partOfSpeech": "noun|verb|adjective|adverb|phrase",
                  "summary": "short meaning",
                  "meanings": [{"partOfSpeech": "...", "items": [{"meaning": "...", "examples": [{"text": "...", "translation": "..."}]}]}]
                }
                """;
        String dirLabel = direction == DictionaryDirection.VI_EN ? "Vietnamese to English" : "English to Vietnamese";
        String userPrompt = "Look up (" + dirLabel + "): " + word;

        try {
            AiGenerationRequest req = new AiGenerationRequest(
                    systemPrompt, userPrompt, 900, 0.2, "application/json", true, Duration.ofSeconds(15));
            return Optional.of(aiProviderRouter.execute(AiTaskType.CHAT, req, result -> {
                JsonNode root = objectMapper.readTree(stripJsonFences(result.content()));
                List<DictionaryLookupResponse.MeaningGroup> meanings = root.has("meanings")
                        ? objectMapper.convertValue(root.get("meanings"), new TypeReference<>() {})
                        : List.of();
                return new DictionaryLookupResponse(
                        root.path("headword").asText(word),
                        direction.name(),
                        root.path("ipa").asText(null),
                        null, null, null,
                        root.path("cefrLevel").asText(null),
                        root.path("partOfSpeech").asText(null),
                        root.path("summary").asText(""),
                        meanings,
                        List.of(), List.of(), null,
                        "AI_FALLBACK",
                        saved
                );
            }));
        } catch (Exception ex) {
            log.debug("AI dictionary fallback failed for '{}': {}", word, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<TranslateTextResponse> translateViaAi(
            String text, String sourceLang, String targetLang, boolean includeIeltsTip) {
        String systemPrompt = """
                You are an accurate English-Vietnamese translator for IELTS learners.
                Return ONLY valid JSON:
                {
                  "translatedText": "...",
                  "ieltsTip": "optional concise IELTS academic vocabulary/grammar note in Vietnamese, or null",
                  "keyVocabulary": [{"term": "...", "meaning": "...", "partOfSpeech": "..."}]
                }
                """;
        String userPrompt = String.format(
                "Translate from %s to %s. %s\nText:\n%s",
                "en".equals(sourceLang) ? "English" : "Vietnamese",
                "vi".equals(targetLang) ? "Vietnamese" : "English",
                includeIeltsTip ? "Include an IELTS Band 7.0+ nuance tip in Vietnamese and up to 3 keyVocabulary items."
                        : "Set ieltsTip to null and keyVocabulary to [].",
                text
        );
        try {
            AiGenerationRequest req = new AiGenerationRequest(
                    systemPrompt, userPrompt, 1200, 0.2, "application/json", true, Duration.ofSeconds(20));
            return Optional.of(aiProviderRouter.execute(AiTaskType.CHAT, req, result -> {
                JsonNode root = objectMapper.readTree(stripJsonFences(result.content()));
                List<TranslateTextResponse.KeyVocabHighlight> highlights = root.has("keyVocabulary")
                        ? objectMapper.convertValue(root.get("keyVocabulary"), new TypeReference<>() {})
                        : List.of();
                String tip = root.path("ieltsTip").isNull() ? null : root.path("ieltsTip").asText(null);
                return new TranslateTextResponse(
                        text,
                        root.path("translatedText").asText(""),
                        sourceLang,
                        targetLang,
                        result.provider().name(),
                        tip,
                        highlights
                );
            }));
        } catch (Exception ex) {
            log.debug("AI translation fallback failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private DictionaryDirection resolveTranslateDirection(String text, String sourceLang, String targetLang) {
        // Smart auto-detection: Vietnamese diacritics always override mismatched EN->VI selection
        if (DictionaryDirection.resolve(text, DictionaryDirection.AUTO) == DictionaryDirection.VI_EN) {
            return DictionaryDirection.VI_EN;
        }
        if ("vi".equalsIgnoreCase(sourceLang) || "en".equalsIgnoreCase(targetLang)) {
            return DictionaryDirection.VI_EN;
        }
        if ("en".equalsIgnoreCase(sourceLang) || "vi".equalsIgnoreCase(targetLang)) {
            return DictionaryDirection.EN_VI;
        }
        return DictionaryDirection.resolve(text, DictionaryDirection.AUTO);
    }

    private boolean isSavedInNotebook(UUID userId, String word) {
        if (userId == null) return false;
        Integer count = jdbc.queryForObject("""
                select count(*) from public.student_vocabulary
                where student_id = ? and lower(term) = lower(?)
                """, Integer.class, userId, word);
        return count != null && count > 0;
    }

    private void recordHistory(UUID userId, String queryText, String direction, String entryType, String summary) {
        if (userId == null || queryText == null || queryText.isBlank()) return;
        try {
            jdbc.update("""
                    insert into public.student_lookup_history (student_id, query_text, direction, entry_type, summary)
                    values (?, ?, ?, ?, ?)
                    """, userId, truncate(queryText, 480), direction, entryType, summary);
        } catch (Exception ex) {
            log.debug("Could not persist lookup history: {}", ex.getMessage());
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private static String stripJsonFences(String raw) {
        if (raw == null) return "{}";
        return raw.replaceAll("^\\s*```(?:json)?\\s*", "")
                .replaceAll("\\s*```\\s*$", "")
                .trim();
    }
}
