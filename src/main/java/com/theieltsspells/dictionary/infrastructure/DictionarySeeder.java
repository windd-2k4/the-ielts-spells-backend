package com.theieltsspells.dictionary.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class DictionarySeeder implements ApplicationRunner {

    private static final int BATCH_SIZE = 2000;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DictionaryProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isSeedOnStartup()) {
            return;
        }
        try {
            Integer existing = jdbcTemplate.queryForObject(
                    "select count(*) from public.dictionary_entries", Integer.class);
            if (existing != null && existing > 0) {
                log.info("Dictionary entries already seeded ({} rows).", existing);
                return;
            }
            Path diskPath = resolveSeedPath();
            boolean hasDiskFile = diskPath != null && Files.exists(diskPath);
            ClassPathResource classpathResource = new ClassPathResource("seed_data/dictionary_entries.jsonl.gz");
            if (!hasDiskFile && !classpathResource.exists()) {
                log.info("Dictionary seed file not found at {}; skipping automatic seed.", properties.getSeedPath());
                return;
            }

            Thread.ofVirtual().name("dictionary-seeder").start(() -> {
                try (InputStream raw = hasDiskFile
                        ? new FileInputStream(diskPath.toFile())
                        : classpathResource.getInputStream();
                     GZIPInputStream gzip = new GZIPInputStream(raw);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
                    seedFromReader(reader);
                } catch (Exception ex) {
                    log.error("Dictionary background seed failed: {}", ex.getMessage(), ex);
                }
            });
        } catch (Exception ex) {
            log.warn("Could not check dictionary seed state: {}", ex.getMessage());
        }
    }

    private Path resolveSeedPath() {
        Path primary = Path.of(properties.getSeedPath());
        if (Files.exists(primary)) return primary;
        Path fallback = Path.of("the-ielts-spells-backend", "data", "dictionary", "dictionary_entries.jsonl.gz");
        return Files.exists(fallback) ? fallback : primary;
    }

    private void seedFromReader(BufferedReader reader) throws Exception {
        log.info("Starting background seeding of dictionary_entries...");
        long startedAt = System.currentTimeMillis();
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        long inserted = 0;

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            JsonNode node = objectMapper.readTree(line);
            String direction = node.path("direction").asText("EN_VI");
            String headword = node.path("headword").asText("");
            String headwordLower = node.path("headwordLower").asText("");
            String normalized = node.path("normalizedHeadword").asText("");
            if (headword.isBlank()) continue;

            String ipa = node.path("ipa").isNull() ? null : node.path("ipa").asText(null);
            String cefr = node.path("cefrLevel").isNull() ? null : node.path("cefrLevel").asText(null);
            String pos = node.path("partOfSpeech").isNull() ? null : node.path("partOfSpeech").asText(null);
            String summary = node.path("summary").isNull() ? null : node.path("summary").asText(null);
            String payloadJson = objectMapper.writeValueAsString(node.path("payload"));
            String source = node.path("source").asText("AVDICT");

            batch.add(new Object[]{
                    direction, headword, headwordLower, normalized,
                    ipa, cefr, pos, summary, payloadJson, source
            });

            if (batch.size() >= BATCH_SIZE) {
                flushBatch(batch);
                inserted += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            flushBatch(batch);
            inserted += batch.size();
        }
        log.info("Dictionary seeding complete: {} rows in {} ms",
                inserted, System.currentTimeMillis() - startedAt);
    }

    private void flushBatch(List<Object[]> batch) {
        int n = batch.size();
        String[] directions = new String[n];
        String[] headwords = new String[n];
        String[] lowers = new String[n];
        String[] norms = new String[n];
        String[] ipas = new String[n];
        String[] cefrs = new String[n];
        String[] poses = new String[n];
        String[] summaries = new String[n];
        String[] payloads = new String[n];
        String[] sources = new String[n];

        for (int i = 0; i < n; i++) {
            Object[] row = batch.get(i);
            directions[i] = (String) row[0];
            headwords[i] = (String) row[1];
            lowers[i] = (String) row[2];
            norms[i] = (String) row[3];
            ipas[i] = (String) row[4];
            cefrs[i] = (String) row[5];
            poses[i] = (String) row[6];
            summaries[i] = (String) row[7];
            payloads[i] = (String) row[8];
            sources[i] = (String) row[9];
        }

        jdbcTemplate.execute((java.sql.Connection con) -> {
            try (var ps = con.prepareStatement("""
                    insert into public.dictionary_entries
                      (direction, headword, headword_lower, normalized_headword,
                       ipa, cefr_level, part_of_speech, summary, payload, source)
                    select d, h, hl, nh, ipa, cefr, pos, sum, p::jsonb, src
                    from unnest(?::text[], ?::text[], ?::text[], ?::text[],
                                ?::text[], ?::text[], ?::text[], ?::text[], ?::text[], ?::text[])
                      as t(d, h, hl, nh, ipa, cefr, pos, sum, p, src)
                    on conflict (direction, headword_lower) do nothing
                    """)) {
                ps.setArray(1, con.createArrayOf("text", directions));
                ps.setArray(2, con.createArrayOf("text", headwords));
                ps.setArray(3, con.createArrayOf("text", lowers));
                ps.setArray(4, con.createArrayOf("text", norms));
                ps.setArray(5, con.createArrayOf("text", ipas));
                ps.setArray(6, con.createArrayOf("text", cefrs));
                ps.setArray(7, con.createArrayOf("text", poses));
                ps.setArray(8, con.createArrayOf("text", summaries));
                ps.setArray(9, con.createArrayOf("text", payloads));
                ps.setArray(10, con.createArrayOf("text", sources));
                ps.executeUpdate();
            }
            return null;
        });
    }
}
