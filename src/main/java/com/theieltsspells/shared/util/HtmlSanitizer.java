package com.theieltsspells.shared.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class HtmlSanitizer {

    private static final Safelist IELTS_SAFELIST = Safelist.relaxed()
            .addTags("span", "sub", "sup", "u", "hr", "div", "figure", "figcaption")
            .addAttributes("table", "border", "cellpadding", "cellspacing")
            .addAttributes("td", "colspan", "rowspan", "align", "valign")
            .addAttributes("th", "colspan", "rowspan", "align", "valign", "scope")
            .addAttributes("img", "src", "alt", "title", "width", "height")
            .addProtocols("img", "src", "http", "https")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer");

    private static final Safelist WRITING_REVIEW_SAFELIST = Safelist.none()
            .addTags("p", "div", "br", "strong", "b", "em", "i", "u", "s", "strike",
                    "span", "font", "ul", "ol", "li", "blockquote", "sub", "sup")
            .addAttributes("p", "style")
            .addAttributes("p", "align")
            .addAttributes("div", "style", "align")
            .addAttributes("span", "style")
            .addAttributes("ul", "style")
            .addAttributes("ol", "style")
            .addAttributes("li", "style")
            .addAttributes("font", "face", "size", "color");

    private static final Set<String> REVIEW_STYLE_PROPERTIES = Set.of(
            "color", "background-color", "font-family", "font-size", "text-align", "list-style-type");
    private static final Pattern COLOR_VALUE = Pattern.compile(
            "(?i)(#[0-9a-f]{3,8}|rgba?\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}(?:\\s*,\\s*(?:0|1|0?\\.\\d+))?\\s*\\)|[a-z]{3,20})");
    private static final Pattern FONT_FAMILY_VALUE = Pattern.compile("[a-zA-Z0-9 _,'-]{1,80}");
    private static final Pattern FONT_SIZE_VALUE = Pattern.compile("(?i)(?:[8-9]|1[0-9]|2[0-8])px|x-small|small|medium|large|x-large");
    private static final Pattern TEXT_ALIGN_VALUE = Pattern.compile("left|center|right|justify");
    private static final Pattern LIST_STYLE_VALUE = Pattern.compile("decimal|disc|circle|square|lower-alpha|upper-alpha");

    private HtmlSanitizer() {
    }

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document.OutputSettings outputSettings = new Document.OutputSettings()
                .prettyPrint(false);
        return Jsoup.clean(html, "", IELTS_SAFELIST, outputSettings).trim();
    }

    public static String stripToPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.parse(html).text().trim();
    }

    /**
     * Sanitizes the deliberately small formatting surface supported by the teacher Writing editor.
     * Scripts, links, media, event handlers, layout/positioning and arbitrary CSS are discarded.
     */
    public static String sanitizeWritingReview(String html) {
        if (html == null || html.isBlank()) return "";
        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        String cleaned = Jsoup.clean(html, "", WRITING_REVIEW_SAFELIST, outputSettings);
        Document document = Jsoup.parseBodyFragment(cleaned);
        document.outputSettings(outputSettings);
        for (Element element : document.body().getAllElements()) {
            if (element.hasAttr("style")) {
                String safeStyle = safeReviewStyle(element.attr("style"));
                if (safeStyle.isBlank()) element.removeAttr("style");
                else element.attr("style", safeStyle);
            }
            if (element.normalName().equals("font")) {
                if (!FONT_FAMILY_VALUE.matcher(element.attr("face")).matches()) element.removeAttr("face");
                if (!element.attr("size").matches("[1-7]")) element.removeAttr("size");
                if (!COLOR_VALUE.matcher(element.attr("color")).matches()) element.removeAttr("color");
            }
        }
        return document.body().html().trim();
    }

    private static String safeReviewStyle(String value) {
        var declarations = new ArrayList<String>();
        for (String raw : value.split(";")) {
            int separator = raw.indexOf(':');
            if (separator < 1) continue;
            String property = raw.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String propertyValue = raw.substring(separator + 1).trim();
            if (!REVIEW_STYLE_PROPERTIES.contains(property) || !isSafeReviewStyleValue(property, propertyValue)) continue;
            declarations.add(property + ": " + propertyValue);
        }
        return String.join("; ", declarations);
    }

    private static boolean isSafeReviewStyleValue(String property, String value) {
        return switch (property) {
            case "color", "background-color" -> COLOR_VALUE.matcher(value).matches();
            case "font-family" -> FONT_FAMILY_VALUE.matcher(value).matches();
            case "font-size" -> FONT_SIZE_VALUE.matcher(value).matches();
            case "text-align" -> TEXT_ALIGN_VALUE.matcher(value).matches();
            case "list-style-type" -> LIST_STYLE_VALUE.matcher(value).matches();
            default -> false;
        };
    }
}
