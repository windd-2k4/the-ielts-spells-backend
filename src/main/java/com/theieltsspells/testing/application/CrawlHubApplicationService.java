package com.theieltsspells.testing.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.persistence.enums.SkillType;
import com.theieltsspells.shared.web.PageResponse;
import com.theieltsspells.testing.application.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CrawlHubApplicationService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AiTestParserService aiParserService;
    private final TestBankApplicationService testBankService;

    // -------------------------------------------------------------------------
    // CRAWL SOURCES CRUD & TRIGGER
    // -------------------------------------------------------------------------

    public List<CrawlSourceDto> listSources() {
        return jdbc.query("select * from public.crawl_sources order by created_at desc", this::mapSource);
    }

    public CrawlSourceDto getSource(UUID id) {
        var list = jdbc.query("select * from public.crawl_sources where id = ?", this::mapSource, id);
        if (list.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy nguồn crawl");
        return list.getFirst();
    }

    @Transactional
    public CrawlSourceDto createSource(SaveCrawlSourceRequest request) {
        UUID id = jdbc.queryForObject("""
                insert into public.crawl_sources (name, source_url, crawler_type, target_skill, config, schedule_cron, is_active)
                values (?, ?, ?, cast(? as public.skill_type), cast(? as jsonb), ?, ?)
                returning id
                """, UUID.class,
                request.name().trim(),
                request.sourceUrl().trim(),
                request.crawlerType() != null ? request.crawlerType() : "scrapy",
                request.targetSkill().name(),
                json(request.config() != null ? request.config() : Map.of()),
                request.scheduleCron(),
                request.isActive() != null ? request.isActive() : true
        );
        return getSource(id);
    }

    @Transactional
    public CrawlSourceDto updateSource(UUID id, SaveCrawlSourceRequest request) {
        getSource(id); // Check existence
        jdbc.update("""
                update public.crawl_sources
                set name = ?, source_url = ?, crawler_type = ?, target_skill = cast(? as public.skill_type),
                    config = cast(? as jsonb), schedule_cron = ?, is_active = ?, updated_at = now()
                where id = ?
                """,
                request.name().trim(),
                request.sourceUrl().trim(),
                request.crawlerType() != null ? request.crawlerType() : "scrapy",
                request.targetSkill().name(),
                json(request.config() != null ? request.config() : Map.of()),
                request.scheduleCron(),
                request.isActive() != null ? request.isActive() : true,
                id
        );
        return getSource(id);
    }

    @Transactional
    public void deleteSource(UUID id) {
        jdbc.update("delete from public.crawl_sources where id = ?", id);
    }

    @Transactional
    public CrawlSourceDto triggerCrawl(UUID sourceId) {
        var source = getSource(sourceId);
        
        // Update status to running
        jdbc.update("update public.crawl_sources set last_status = 'running', last_crawled_at = now() where id = ?", sourceId);

        // Seed/Simulate a new raw crawled test payload into raw_crawled_tests
        String sampleTitle = "Crawled Test - " + source.name() + " (" + (source.totalCrawled() + 1) + ")";
        String sampleText = """
                READING PASSAGE 1
                You should spend about 20 minutes on Questions 1-13, which are based on Reading Passage 1 below.

                The History of Artificial Intelligence in Education
                Artificial Intelligence (AI) has rapidly transformed the educational landscape over the past decade.
                From automated grading systems to personalized learning algorithms, AI tools have redefined how educators teach and students learn.
                Early educational software in the 1980s relied on rigid, rule-based decision trees.
                However, modern platforms leverage deep neural networks and natural language processing to evaluate student comprehension in real time.

                Questions 1-5
                Do the following statements agree with the information given in Reading Passage 1?
                In boxes 1-5 on your answer sheet, write:
                TRUE if the statement agrees with the information
                FALSE if the statement contradicts the information
                NOT GIVEN if there is no information on this

                1. Early educational software in the 1980s used deep neural networks.
                2. Automated grading systems can evaluate student answers in real time.
                3. Decision trees are still the primary technology used in 2026 AI platforms.
                4. AI has transformed both teaching and learning practices.
                5. Natural language processing is no longer used in modern education tools.

                Questions 6-10
                Complete the sentences below.
                Choose NO MORE THAN TWO WORDS from the passage for each answer.

                6. Over the past decade, AI has transformed the educational ________.
                7. Software in the 1980s depended on rigid ________ decision trees.
                8. Modern platforms evaluate student comprehension in ________.
                """;

        Map<String, Object> rawPayload = Map.of(
                "extracted_text", sampleText,
                "source_url", source.sourceUrl(),
                "crawled_at", OffsetDateTime.now().toString(),
                "crawler_type", source.crawlerType()
        );

        jdbc.update("""
                insert into public.raw_crawled_tests (source_id, source_test_id, title, skill, source_url, raw_payload, status)
                values (?, ?, ?, cast(? as public.skill_type), ?, cast(? as jsonb), 'pending')
                """, sourceId, "OPEN_REF_" + System.currentTimeMillis(), sampleTitle, source.targetSkill().name(),
                source.sourceUrl(), json(rawPayload));

        // Update total crawled & last_status
        jdbc.update("update public.crawl_sources set total_crawled = total_crawled + 1, last_status = 'success' where id = ?", sourceId);

        return getSource(sourceId);
    }

    // -------------------------------------------------------------------------
    // RAW CRAWLED TESTS STAGING AREA
    // -------------------------------------------------------------------------

    public PageResponse<RawCrawledTestDto> listRawTests(String query, SkillType skill, String status, UUID sourceId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);

        var where = new StringBuilder(" where 1=1");
        var args = new ArrayList<Object>();

        if (query != null && !query.isBlank()) {
            where.append(" and lower(r.title) like ?");
            args.add("%" + query.trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (skill != null) {
            where.append(" and r.skill = cast(? as public.skill_type)");
            args.add(skill.name());
        }
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            where.append(" and r.status = ?");
            args.add(status.trim().toLowerCase(Locale.ROOT));
        }
        if (sourceId != null) {
            where.append(" and r.source_id = ?");
            args.add(sourceId);
        }

        var countSql = "select count(*) from public.raw_crawled_tests r" + where;
        Long total = jdbc.queryForObject(countSql, Long.class, args.toArray());

        var selectSql = """
                select r.*, s.name as source_name
                from public.raw_crawled_tests r
                left join public.crawl_sources s on r.source_id = s.id
                """ + where + " order by r.crawled_at desc limit ? offset ?";

        var dataArgs = new ArrayList<>(args);
        dataArgs.add(safeSize);
        dataArgs.add(safePage * safeSize);

        List<RawCrawledTestDto> content = jdbc.query(selectSql, this::mapRawTest, dataArgs.toArray());
        long count = total == null ? 0 : total;
        int totalPages = (int) Math.ceil((double) count / safeSize);

        return new PageResponse<>(content, safePage, safeSize, count, totalPages, safePage == 0, safePage + 1 >= totalPages);
    }

    public RawCrawledTestDto getRawTest(UUID id) {
        var list = jdbc.query("""
                select r.*, s.name as source_name
                from public.raw_crawled_tests r
                left join public.crawl_sources s on r.source_id = s.id
                where r.id = ?
                """, this::mapRawTest, id);
        if (list.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy bài test thô");
        return list.getFirst();
    }

    @Transactional
    public RawCrawledTestDto parseRawTest(UUID id, AiParserProvider provider, String teacherInstructions) {
        var rawTest = getRawTest(id);
        String extractedText = "";

        if (rawTest.rawPayload() != null && rawTest.rawPayload().containsKey("extracted_text")) {
            extractedText = String.valueOf(rawTest.rawPayload().get("extracted_text"));
        } else if (rawTest.rawPayload() != null && rawTest.rawPayload().containsKey("html")) {
            extractedText = String.valueOf(rawTest.rawPayload().get("html"));
        } else {
            extractedText = rawTest.title();
        }

        AiTestParseRequest parseRequest = new AiTestParseRequest(
                extractedText,
                rawTest.skill(),
                "FULL_TEST",
                rawTest.title(),
                rawTest.sourceUrl(),
                provider != null ? provider : AiParserProvider.AUTO,
                teacherInstructions
        );

        AiTestParseResponse parseResponse = aiParserService.parse(parseRequest);

        // Convert parsed structure to JSON map
        Map<String, Object> parsedStructure = objectMapper.convertValue(parseResponse, new TypeReference<>() {});

        jdbc.update("""
                update public.raw_crawled_tests
                set parsed_structure = cast(? as jsonb), status = 'parsed', error_message = null
                where id = ?
                """, json(parsedStructure), id);

        return getRawTest(id);
    }

    @Transactional
    public RawCrawledTestDto importRawTestToTestBank(UUID id, UUID actor) {
        var rawTest = getRawTest(id);
        if (rawTest.parsedStructure() == null || rawTest.parsedStructure().isEmpty()) {
            // Auto parse if not parsed yet
            rawTest = parseRawTest(id, AiParserProvider.OFFLINE_REGEX, "Import tự động từ Crawl Hub");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> builderContent = (Map<String, Object>) rawTest.parsedStructure().get("builderContent");
        if (builderContent == null) builderContent = Map.of();

        TestBankRequest createRequest = new TestBankRequest(
                rawTest.title(),
                "Nhập tự động từ kho Crawl Hub: " + (rawTest.sourceName() != null ? rawTest.sourceName() : "Nguồn Open Source"),
                rawTest.skill(),
                "FULL_TEST",
                60,
                "v1.0",
                List.of("CRAWLED", rawTest.skill().name()),
                builderContent,
                1
        );

        TestBankResponse createdTest = testBankService.create(createRequest, actor);

        jdbc.update("""
                update public.raw_crawled_tests
                set imported_test_id = ?, status = 'imported', imported_at = now()
                where id = ?
                """, createdTest.id(), id);

        return getRawTest(id);
    }

    @Transactional
    public RawCrawledTestDto updateRawTestStatus(UUID id, String status) {
        getRawTest(id);
        jdbc.update("update public.raw_crawled_tests set status = ? where id = ?", status.trim().toLowerCase(Locale.ROOT), id);
        return getRawTest(id);
    }

    // -------------------------------------------------------------------------
    // ROW MAPPERS & UTILS
    // -------------------------------------------------------------------------

    private CrawlSourceDto mapSource(ResultSet rs, int rowNum) throws SQLException {
        String skillStr = rs.getString("target_skill");
        return new CrawlSourceDto(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("source_url"),
                rs.getString("crawler_type"),
                skillStr != null ? SkillType.valueOf(skillStr.trim().toUpperCase(Locale.ROOT)) : SkillType.READING,
                jsonToMap(rs.getString("config")),
                rs.getString("schedule_cron"),
                rs.getBoolean("is_active"),
                rs.getObject("last_crawled_at", OffsetDateTime.class),
                rs.getString("last_status"),
                rs.getInt("total_crawled"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private RawCrawledTestDto mapRawTest(ResultSet rs, int rowNum) throws SQLException {
        String skillStr = rs.getString("skill");
        return new RawCrawledTestDto(
                rs.getObject("id", UUID.class),
                rs.getObject("source_id", UUID.class),
                rs.getString("source_name"),
                rs.getString("source_test_id"),
                rs.getString("title"),
                skillStr != null ? SkillType.valueOf(skillStr.trim().toUpperCase(Locale.ROOT)) : SkillType.READING,
                rs.getString("source_url"),
                rs.getString("status"),
                rs.getObject("imported_test_id", UUID.class),
                rs.getString("error_message"),
                rs.getObject("crawled_at", OffsetDateTime.class),
                rs.getObject("imported_at", OffsetDateTime.class),
                jsonToMap(rs.getString("raw_payload")),
                jsonToMap(rs.getString("parsed_structure"))
        );
    }

    private Map<String, Object> jsonToMap(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(rawJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
