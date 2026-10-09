package com.theieltsspells.testing.infrastructure.crawl;

import com.theieltsspells.shared.persistence.enums.SkillType;
import com.theieltsspells.testing.application.dto.CrawlSourceDto;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JsoupOpenSourceTestCrawlerTests {

    @Test
    void discoversUniqueDetailPagesAndRejectsIncompleteTests() {
        URI listingUri = URI.create("https://mini-ielts.com/reading");
        URI completeUri = URI.create("https://mini-ielts.com/1518/reading/australian-artist-margaret-preston");
        URI incompleteUri = URI.create("https://mini-ielts.com/1519/reading/incomplete-test");
        Map<String, FetchedPage> pages = new HashMap<>();
        pages.put(listingUri.toString(), page(listingUri, """
                <html><body>
                  <a href="/1518/reading/australian-artist-margaret-preston">Test</a>
                  <a href="/1518/reading/australian-artist-margaret-preston?utm_source=duplicate">Duplicate card link</a>
                  <a href="/1518/reading/australian-artist-margaret-preston/solution">Solution</a>
                  <a href="/1519/reading/incomplete-test">Incomplete</a>
                </body></html>
                """));
        pages.put(completeUri.toString(), page(completeUri, completeTestHtml()));
        pages.put(incompleteUri.toString(), page(incompleteUri, """
                <div class="readingPassage"><h2>Incomplete test</h2><p>Short passage.</p></div>
                <div class="exam-content"><h2>Questions 1 - 2</h2><p>1 First?</p><p>2 Second?</p></div>
                """));

        JsoupOpenSourceTestCrawler crawler = new JsoupOpenSourceTestCrawler(uri -> {
            FetchedPage result = pages.get(uri.toString());
            if (result == null) {
                throw new IllegalArgumentException("Unexpected URI " + uri);
            }
            return result;
        });

        var result = crawler.crawl(source(Map.of(
                "maxListingPages", 1,
                "minTextLength", 500,
                "minQuestionCount", 3,
                "minQuestionTextLength", 50
        )), Set.of());

        assertThat(result.discoveredCount()).isEqualTo(2);
        assertThat(result.documents()).hasSize(1);
        assertThat(result.skippedIncompleteCount()).isEqualTo(1);
        assertThat(result.warnings()).singleElement().asString().contains("incomplete-test");
        assertThat(result.documents().getFirst().title()).isEqualTo("Australian artist Margaret Preston");
        assertThat(result.documents().getFirst().sourceUrl()).isEqualTo(completeUri.toString());
        assertThat(result.documents().getFirst().questionCount()).isEqualTo(3);
        assertThat(result.documents().getFirst().extractedText())
                .contains("Australian artist Margaret Preston", "Questions 1 - 3", "1 First question?");
        assertThat(result.documents().getFirst().html()).contains("readingPassage", "exam-content");
        assertThat(result.documents().getFirst().metadata()).containsEntry("complete", true);
    }

    @Test
    void skipsAlreadyKnownSourceTestIdInsteadOfFetchingItAgain() {
        URI listingUri = URI.create("https://mini-ielts.com/reading");
        URI detailUri = URI.create("https://mini-ielts.com/1518/reading/australian-artist-margaret-preston");
        Map<String, Integer> fetchCounts = new HashMap<>();
        CrawlPageFetcher fetcher = uri -> {
            fetchCounts.merge(uri.toString(), 1, Integer::sum);
            if (uri.equals(listingUri)) {
                return page(uri, "<a href=\"/1518/reading/australian-artist-margaret-preston\">Test</a>");
            }
            if (uri.equals(detailUri)) {
                return page(uri, completeTestHtml());
            }
            throw new IllegalArgumentException("Unexpected URI " + uri);
        };
        JsoupOpenSourceTestCrawler crawler = new JsoupOpenSourceTestCrawler(fetcher);
        CrawlSourceDto source = source(Map.of(
                "maxListingPages", 1,
                "minTextLength", 500,
                "minQuestionCount", 3,
                "minQuestionTextLength", 50
        ));

        var firstRun = crawler.crawl(source, Set.of());
        var secondRun = crawler.crawl(source, Set.of(firstRun.documents().getFirst().sourceTestId()));

        assertThat(secondRun.documents()).isEmpty();
        assertThat(secondRun.skippedKnownCount()).isEqualTo(1);
        assertThat(fetchCounts.get(detailUri.toString())).isEqualTo(1);
    }

    private static CrawlSourceDto source(Map<String, Object> config) {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-05T10:00:00+07:00");
        return new CrawlSourceDto(
                UUID.randomUUID(),
                "mini ielts",
                "https://mini-ielts.com/reading",
                "OPEN_REF",
                SkillType.READING,
                config,
                null,
                true,
                null,
                null,
                0,
                now,
                now
        );
    }

    private static FetchedPage page(URI uri, String body) {
        return new FetchedPage(uri, "text/html", body);
    }

    private static String completeTestHtml() {
        String passage = "Margaret Preston developed a distinctive Australian visual language through colour and form. ".repeat(12);
        return """
                <html><head><title>Fallback title</title></head><body>
                  <div class="readingPassage">
                    <h2>Australian artist Margaret Preston</h2>
                    <p>%s</p>
                  </div>
                  <div class="exam-content">
                    <h2>Questions 1 - 3</h2>
                    <p>1 First question?</p>
                    <p>2 Second question?</p>
                    <p>3 Third question?</p>
                  </div>
                </body></html>
                """.formatted(passage);
    }
}
