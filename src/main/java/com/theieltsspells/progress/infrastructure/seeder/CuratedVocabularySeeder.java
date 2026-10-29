package com.theieltsspells.progress.infrastructure.seeder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class CuratedVocabularySeeder implements ApplicationRunner {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            ClassPathResource resource = new ClassPathResource("seed_data/youpass_all_vocab.json");
            if (!resource.exists()) {
                log.warn("Seed file seed_data/youpass_all_vocab.json not found. Skipping seeder.");
                return;
            }

            Integer existingDecks = jdbcTemplate.queryForObject(
                    "select count(*) from public.curated_vocabulary_decks", Integer.class);

            if (existingDecks == null || existingDecks == 0) {
                seedAll(resource);
            } else {
                enrichExistingItemsIfNecessary(resource);
            }
        } catch (Exception e) {
            log.error("Failed to seed curated vocabulary: {}", e.getMessage(), e);
        }
    }

    private void seedAll(ClassPathResource resource) throws Exception {
        log.info("Starting to seed curated vocabulary from {}...", resource.getPath());
        try (InputStream is = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            if (!root.isArray()) return;

            int totalDecks = 0;
            int totalWords = 0;

            for (int i = 0; i < root.size(); i++) {
                JsonNode deckNode = root.get(i);
                int rawId = deckNode.path("id").asInt(i);
                String name = deckNode.path("name").asText("Bộ từ " + (i + 1));
                JsonNode wordsNode = deckNode.path("words");
                int wordCount = wordsNode.isArray() ? wordsNode.size() : 0;

                String category = determineCategory(rawId, name);
                String slug = toSlug(name) + "-" + rawId;
                String description = buildDescription(category, name);
                UUID deckId = UUID.randomUUID();

                jdbcTemplate.update("""
                        insert into public.curated_vocabulary_decks
                          (id, slug, name, category, description, word_count, order_index, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, now())
                        """, deckId, slug, name, category, description, wordCount, i);

                if (wordsNode.isArray() && wordCount > 0) {
                    List<Object[]> batchParams = new ArrayList<>();
                    for (int w = 0; w < wordsNode.size(); w++) {
                        JsonNode word = wordsNode.get(w);
                        UUID itemId = UUID.randomUUID();
                        String term = word.path("en").asText("").trim();
                        if (term.isEmpty()) {
                            term = word.path("key").asText("word_" + w);
                        }
                        String pos = word.path("pos").asText("");
                        String ipa = word.path("ipa").asText("");
                        String vi = word.path("vi").asText("").trim();
                        String ex = word.path("ex").asText("");
                        String exVi = word.path("exVi").asText("");
                        String chunk = word.hasNonNull("chunk") ? word.path("chunk").asText(null) : null;
                        String collsJson = word.hasNonNull("colls") ? word.path("colls").toString() : null;

                        ObjectNode exObj = objectMapper.createObjectNode();
                        if (word.hasNonNull("cwe")) exObj.set("cwe", word.get("cwe"));
                        if (word.hasNonNull("cwh")) exObj.set("cwh", word.get("cwh"));
                        if (word.hasNonNull("w3e")) exObj.set("w3e", word.get("w3e"));
                        if (word.hasNonNull("w3h")) exObj.set("w3h", word.get("w3h"));
                        String exerciseJson = exObj.size() > 0 ? exObj.toString() : null;

                        String tip = word.path("tip").asText("");
                        String drillsJson = word.hasNonNull("drills") ? word.path("drills").toString() : null;

                        batchParams.add(new Object[]{
                                itemId, deckId, term, pos, ipa, vi, ex, exVi, chunk, collsJson, exerciseJson, tip, drillsJson, w
                        });
                    }

                    jdbcTemplate.batchUpdate("""
                            insert into public.curated_vocabulary_items
                              (id, deck_id, term, part_of_speech, phonetic, meaning, example_sentence,
                               example_sentence_vi, chunk, collocations, exercise_json, tip, drills_json, order_index, created_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, now())
                            """, batchParams);
                    totalWords += wordCount;
                }
                totalDecks++;
            }

            log.info("Successfully seeded {} curated vocabulary decks and {} vocabulary items.", totalDecks, totalWords);
        }
    }

    private void enrichExistingItemsIfNecessary(ClassPathResource resource) {
        try {
            Integer countWithChunk = jdbcTemplate.queryForObject(
                    "select count(*) from public.curated_vocabulary_items where chunk is not null", Integer.class);
            if (countWithChunk != null && countWithChunk > 0) {
                log.info("Curated vocabulary items already enriched with chunk/collocations. Skipping.");
                return;
            }

            log.info("Enriching existing curated vocabulary items with chunk, collocations, and exercises...");
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                if (!root.isArray()) return;

                int updatedCount = 0;
                for (int i = 0; i < root.size(); i++) {
                    JsonNode deckNode = root.get(i);
                    int rawId = deckNode.path("id").asInt(i);
                    String name = deckNode.path("name").asText("Bộ từ " + (i + 1));
                    String slug = toSlug(name) + "-" + rawId;
                    JsonNode wordsNode = deckNode.path("words");
                    if (!wordsNode.isArray()) continue;

                    UUID deckId = jdbcTemplate.query(
                            "select id from public.curated_vocabulary_decks where slug = ?",
                            (rs, row) -> rs.getObject("id", UUID.class),
                            slug
                    ).stream().findFirst().orElse(null);

                    if (deckId == null) continue;

                    List<Object[]> batchParams = new ArrayList<>();
                    for (int w = 0; w < wordsNode.size(); w++) {
                        JsonNode word = wordsNode.get(w);
                        String chunk = word.hasNonNull("chunk") ? word.path("chunk").asText(null) : null;
                        String collsJson = word.hasNonNull("colls") ? word.path("colls").toString() : null;

                        ObjectNode exObj = objectMapper.createObjectNode();
                        if (word.hasNonNull("cwe")) exObj.set("cwe", word.get("cwe"));
                        if (word.hasNonNull("cwh")) exObj.set("cwh", word.get("cwh"));
                        if (word.hasNonNull("w3e")) exObj.set("w3e", word.get("w3e"));
                        if (word.hasNonNull("w3h")) exObj.set("w3h", word.get("w3h"));
                        String exerciseJson = exObj.size() > 0 ? exObj.toString() : null;

                        batchParams.add(new Object[]{chunk, collsJson, exerciseJson, deckId, w});
                    }

                    jdbcTemplate.batchUpdate("""
                            update public.curated_vocabulary_items
                            set chunk = ?,
                                collocations = ?::jsonb,
                                exercise_json = ?::jsonb
                            where deck_id = ? and order_index = ?
                            """, batchParams);
                    updatedCount += batchParams.size();
                }
                log.info("Successfully enriched {} curated vocabulary items.", updatedCount);
            }
        } catch (Exception e) {
            log.warn("Enrichment skipped or failed: {}", e.getMessage());
        }
    }

    private String determineCategory(int rawId, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("root") || lower.contains("trunk") || lower.contains("bud") || lower.contains("bloom")) {
            return "INTENSIVE";
        }
        if (lower.contains("(basic)") || lower.startsWith("basic ") || rawId <= 14) {
            return "BASIC";
        }
        return "ADVANCED";
    }

    private String buildDescription(String category, String name) {
        return switch (category) {
            case "BASIC" -> "Bộ từ vựng nền tảng trọng tâm cho IELTS Band 4.5 - 6.0.";
            case "ADVANCED" -> "Bộ từ vựng học thuật nâng cao cho IELTS Band 6.5 - 8.0+.";
            default -> "Bộ từ vựng tăng cường kỹ năng theo lộ trình tuần chuyên sâu.";
        };
    }

    private String toSlug(String input) {
        if (input == null) return "deck";
        String nowhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NON_LATIN.matcher(normalized).replaceAll("");
        slug = slug.toLowerCase(Locale.ROOT).replaceAll("-+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "deck" : slug;
    }
}
