package com.theieltsspells.shared.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

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
}
