package com.theieltsspells.testing.infrastructure.crawl;

import com.theieltsspells.testing.application.crawl.CrawlBatchResult;
import com.theieltsspells.testing.application.crawl.CrawledTestDocument;
import com.theieltsspells.testing.application.crawl.OpenSourceTestCrawler;
import com.theieltsspells.testing.application.dto.CrawlSourceDto;
import com.theieltsspells.shared.persistence.enums.SkillType;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class JsoupOpenSourceTestCrawler implements OpenSourceTestCrawler {

    private static final Pattern QUESTION_RANGE = Pattern.compile("(?i)questions?\\s+(\\d{1,3})\\s*[-–—]\\s*(\\d{1,3})");
    private static final Pattern NUMBERED_QUESTION = Pattern.compile("(?m)^\\s*(\\d{1,3})[.)]\\s+\\S+");
    private static final String REMOVE_SELECTOR = String.join(", ",
            "script", "style", "noscript", "svg", "canvas", "nav", "footer",
            ".ads", ".formatbar", ".workspace", ".splitter", ".splitter-horizontal",
            "button", "input", "select", "textarea");

    private final CrawlPageFetcher pageFetcher;

    @Override
    public CrawlBatchResult crawl(CrawlSourceDto source, Set<String> knownSourceTestIds) {
        URI sourceUri = parseUri(source.sourceUrl());
        CrawlConfiguration configuration = CrawlConfiguration.from(source, sourceUri);
        Set<String> knownIds = knownSourceTestIds == null ? Set.of() : knownSourceTestIds;

        LinkedHashMap<String, URI> candidates = new LinkedHashMap<>();
        Set<String> discoveredIds = new HashSet<>();
        int skippedKnown = 0;

        if (configuration.detailUrlPattern().matcher(sourceUri.getPath()).matches()) {
            String sourceTestId = stableId(canonicalize(sourceUri).toString());
            discoveredIds.add(sourceTestId);
            if (knownIds.contains(sourceTestId)) {
                skippedKnown++;
            } else {
                candidates.put(sourceTestId, canonicalize(sourceUri));
            }
        } else {
            int startPage = 1;
            for (int pageOffset = 0;
                 pageOffset < configuration.maxListingPages() && candidates.size() < configuration.maxItemsPerRun();
                 pageOffset++) {
                URI listingUri = pageOffset == 0 && startPage == 1
                        ? sourceUri
                        : withPage(sourceUri, configuration.pageParameter(), startPage + pageOffset);
                FetchedPage listingPage = pageFetcher.fetch(listingUri);
                Document listing = Jsoup.parse(listingPage.body(), listingPage.uri().toString());
                for (Element link : listing.select(configuration.detailLinkSelector())) {
                    String href = link.attr("abs:href");
                    if (href.isBlank()) {
                        continue;
                    }
                    URI detailUri;
                    try {
                        detailUri = canonicalize(URI.create(href));
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    if (!sameHost(sourceUri, detailUri)
                            || !configuration.detailUrlPattern().matcher(detailUri.getPath()).matches()) {
                        continue;
                    }
                    String sourceTestId = stableId(detailUri.toString());
                    if (!discoveredIds.add(sourceTestId)) {
                        continue;
                    }
                    if (knownIds.contains(sourceTestId)) {
                        skippedKnown++;
                        continue;
                    }
                    candidates.put(sourceTestId, detailUri);
                    if (candidates.size() >= configuration.maxItemsPerRun()) {
                        break;
                    }
                }
            }
        }

        List<CrawledTestDocument> documents = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int skippedIncomplete = 0;

        for (Map.Entry<String, URI> candidate : candidates.entrySet()) {
            try {
                CrawledTestDocument document = extract(candidate.getKey(), candidate.getValue(), configuration);
                if (document.extractedText().length() < configuration.minTextLength()
                        || document.questionCount() < configuration.minQuestionCount()) {
                    skippedIncomplete++;
                    warnings.add("Bỏ qua đề chưa đủ nội dung: " + candidate.getValue()
                            + " (" + document.questionCount() + " câu, " + document.extractedText().length() + " ký tự)");
                    continue;
                }
                documents.add(document);
            } catch (RuntimeException exception) {
                skippedIncomplete++;
                warnings.add("Không đọc được đề " + candidate.getValue() + ": " + rootMessage(exception));
            }
        }

        return new CrawlBatchResult(
                documents,
                discoveredIds.size(),
                skippedKnown,
                skippedIncomplete,
                warnings
        );
    }

    private CrawledTestDocument extract(String sourceTestId, URI detailUri, CrawlConfiguration configuration) {
        FetchedPage page = pageFetcher.fetch(detailUri);
        Document document = Jsoup.parse(page.body(), page.uri().toString());
        Elements selectedRoots = document.select(configuration.contentSelector());
        List<Element> roots = selectedRoots.stream()
                .filter(element -> element.parents().stream().noneMatch(selectedRoots::contains))
                .toList();
        if (roots.isEmpty()) {
            throw new IllegalStateException("Không tìm thấy vùng nội dung bằng selector " + configuration.contentSelector());
        }

        int questionTextLength = 0;
        if (configuration.questionSelector() != null) {
            Elements questionRoots = document.select(configuration.questionSelector());
            if (questionRoots.isEmpty()) {
                throw new IllegalStateException("Không tìm thấy phần câu hỏi bằng selector " + configuration.questionSelector());
            }
            questionTextLength = questionRoots.stream()
                    .map(Element::clone)
                    .peek(element -> element.select(REMOVE_SELECTOR).remove())
                    .map(this::structuredText)
                    .map(this::normalizeLines)
                    .mapToInt(String::length)
                    .sum();
            if (questionTextLength < configuration.minQuestionTextLength()) {
                throw new IllegalStateException("Phần câu hỏi quá ngắn: " + questionTextLength + " ký tự");
            }
        }

        StringBuilder text = new StringBuilder();
        StringBuilder html = new StringBuilder();
        for (Element root : roots) {
            Element cleanRoot = root.clone();
            cleanRoot.select(REMOVE_SELECTOR).remove();
            String structured = structuredText(cleanRoot);
            if (!structured.isBlank()) {
                if (!text.isEmpty()) {
                    text.append("\n\n");
                    html.append("\n");
                }
                text.append(structured);
                html.append(cleanRoot.outerHtml());
            }
        }

        String extractedText = normalizeLines(text.toString());
        int questionCount = detectQuestionCount(extractedText);
        String title = extractTitle(document, configuration.titleSelector(), detailUri);
        String canonicalUrl = canonicalize(page.uri()).toString();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("question_count", questionCount);
        metadata.put("question_text_length", questionTextLength);
        metadata.put("text_length", extractedText.length());
        metadata.put("content_selector", configuration.contentSelector());
        metadata.put("canonical_url", canonicalUrl);
        metadata.put("complete", extractedText.length() >= configuration.minTextLength()
                && questionCount >= configuration.minQuestionCount());

        return new CrawledTestDocument(
                sourceTestId,
                title,
                canonicalUrl,
                extractedText,
                html.toString(),
                questionCount,
                stableId(extractedText),
                metadata
        );
    }

    private String extractTitle(Document document, String titleSelector, URI detailUri) {
        Element titleElement = document.selectFirst(titleSelector);
        String title = titleElement == null ? "" : titleElement.text().trim();
        if (title.isBlank()) {
            Element openGraphTitle = document.selectFirst("meta[property=og:title]");
            title = openGraphTitle == null ? document.title() : openGraphTitle.attr("content").trim();
        }
        if (title.isBlank()) {
            title = detailUri.getPath().replaceAll(".*/", "").replace('-', ' ');
        }
        return title.replaceFirst("(?i)\\s*[-|]\\s*(IELTS|mini IELTS).*$", "").trim();
    }

    private int detectQuestionCount(String text) {
        Set<Integer> questionNumbers = new HashSet<>();
        Matcher rangeMatcher = QUESTION_RANGE.matcher(text);
        while (rangeMatcher.find()) {
            int first = Integer.parseInt(rangeMatcher.group(1));
            int last = Integer.parseInt(rangeMatcher.group(2));
            if (first > 0 && last >= first && last - first < 100) {
                for (int number = first; number <= last; number++) {
                    questionNumbers.add(number);
                }
            }
        }
        Matcher numberedMatcher = NUMBERED_QUESTION.matcher(text);
        while (numberedMatcher.find()) {
            questionNumbers.add(Integer.parseInt(numberedMatcher.group(1)));
        }
        return questionNumbers.size();
    }

    private String structuredText(Element root) {
        StringBuilder output = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof Element element) {
                    if ("br".equals(element.normalName()) || element.tag().isBlock()) {
                        appendLineBreak(output);
                    }
                } else if (node instanceof TextNode textNode) {
                    String value = textNode.text().replaceAll("\\s+", " ").trim();
                    if (!value.isBlank()) {
                        if (!output.isEmpty() && !Character.isWhitespace(output.charAt(output.length() - 1))) {
                            output.append(' ');
                        }
                        output.append(value);
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element element && element.tag().isBlock()) {
                    appendLineBreak(output);
                }
            }
        }, root);
        return output.toString();
    }

    private void appendLineBreak(StringBuilder output) {
        if (!output.isEmpty() && output.charAt(output.length() - 1) != '\n') {
            output.append('\n');
        }
    }

    private String normalizeLines(String value) {
        return Arrays.stream(value.replace('\u00a0', ' ').split("\\R"))
                .map(line -> line.replaceAll("\\s+", " ").trim())
                .filter(line -> !line.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private URI parseUri(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
            return canonicalize(uri);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("URL nguồn crawl không hợp lệ: " + value, exception);
        }
    }

    private URI canonicalize(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String query = cleanQuery(uri.getRawQuery());
        try {
            return new URI(scheme, null, host, uri.getPort(), path, query, null);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Không thể chuẩn hóa URL: " + uri, exception);
        }
    }

    private String cleanQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        String cleaned = Arrays.stream(rawQuery.split("&"))
                .filter(part -> {
                    String key = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
                    return !key.startsWith("utm_") && !key.equals("fbclid") && !key.equals("gclid");
                })
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        return cleaned.isBlank() ? null : cleaned;
    }

    private URI withPage(URI sourceUri, String pageParameter, int page) {
        if (pageParameter == null || pageParameter.isBlank()) {
            return sourceUri;
        }
        String encodedName = URLEncoder.encode(pageParameter, StandardCharsets.UTF_8);
        List<String> parts = new ArrayList<>();
        if (sourceUri.getRawQuery() != null && !sourceUri.getRawQuery().isBlank()) {
            for (String part : sourceUri.getRawQuery().split("&")) {
                if (!part.split("=", 2)[0].equals(encodedName)) {
                    parts.add(part);
                }
            }
        }
        parts.add(encodedName + "=" + page);
        try {
            return new URI(sourceUri.getScheme(), null, sourceUri.getHost(), sourceUri.getPort(),
                    sourceUri.getPath(), String.join("&", parts), null);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Không thể tạo URL phân trang", exception);
        }
    }

    private boolean sameHost(URI left, URI right) {
        return left.getHost() != null && right.getHost() != null && left.getHost().equalsIgnoreCase(right.getHost());
    }

    private String stableId(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 không khả dụng", exception);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record CrawlConfiguration(
            int maxItemsPerRun,
            int maxListingPages,
            int minQuestionCount,
            int minTextLength,
            int minQuestionTextLength,
            String detailLinkSelector,
            Pattern detailUrlPattern,
            String contentSelector,
            String questionSelector,
            String titleSelector,
            String pageParameter
    ) {
        private static CrawlConfiguration from(CrawlSourceDto source, URI sourceUri) {
            Map<String, Object> config = source.config() == null ? Map.of() : source.config();
            boolean miniIelts = sourceUri.getHost().equalsIgnoreCase("mini-ielts.com")
                    || sourceUri.getHost().toLowerCase(Locale.ROOT).endsWith(".mini-ielts.com");
            String skill = source.targetSkill().name().toLowerCase(Locale.ROOT);
            boolean miniIeltsReading = miniIelts && source.targetSkill() == SkillType.READING;
            String defaultDetailPattern = miniIelts
                    ? "^/\\d+/" + Pattern.quote(skill) + "/[^/?#]+$"
                    : "(?i)^/.*/?" + Pattern.quote(skill) + "/[^/?#]+$";
            String defaultContentSelector = miniIeltsReading
                    ? ".readingPassage, .exam-content"
                    : "article, main, .entry-content, .post-content, .test-container";
            String defaultQuestionSelector = miniIeltsReading ? ".exam-content" : null;
            String defaultTitleSelector = miniIeltsReading ? ".readingPassage h2" : "h1, article h2, main h2";
            return new CrawlConfiguration(
                    integer(config, "maxItemsPerRun", 10, 1, 25),
                    integer(config, "maxListingPages", miniIelts ? 5 : 1, 1, 10),
                    integer(config, "minQuestionCount", 10, 1, 100),
                    integer(config, "minTextLength", 1500, 500, 100_000),
                    integer(config, "minQuestionTextLength", 200, 50, 20_000),
                    string(config, "detailLinkSelector", "a[href]"),
                    Pattern.compile(string(config, "detailUrlRegex", defaultDetailPattern)),
                    string(config, "contentSelector", defaultContentSelector),
                    string(config, "questionSelector", defaultQuestionSelector),
                    string(config, "titleSelector", defaultTitleSelector),
                    string(config, "pageParameter", miniIelts ? "page" : null)
            );
        }

        private static int integer(Map<String, Object> config, String key, int fallback, int min, int max) {
            Object value = config.get(key);
            int parsed = value instanceof Number number ? number.intValue() : fallback;
            return Math.max(min, Math.min(parsed, max));
        }

        private static String string(Map<String, Object> config, String key, String fallback) {
            Object value = config.get(key);
            if (value == null || String.valueOf(value).isBlank()) {
                return fallback;
            }
            return String.valueOf(value).trim();
        }
    }
}
