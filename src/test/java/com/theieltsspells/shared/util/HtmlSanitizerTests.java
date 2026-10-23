package com.theieltsspells.shared.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlSanitizerTests {

    @Test
    void removesExecutableMarkupAndStylingEscapeHatches() {
        String sanitized = HtmlSanitizer.sanitize("""
                <div id="escape" class="hidden" style="position:fixed">
                  <script>alert(1)</script>
                  <img src="data:text/html;base64,PHNjcmlwdD4=" onerror="alert(2)">
                  <p onclick="alert(3)">Safe text</p>
                </div>
                """);

        assertTrue(sanitized.contains("Safe text"));
        assertFalse(sanitized.contains("<script"));
        assertFalse(sanitized.contains("onerror"));
        assertFalse(sanitized.contains("onclick"));
        assertFalse(sanitized.contains("style="));
        assertFalse(sanitized.contains("class="));
        assertFalse(sanitized.contains("id="));
        assertFalse(sanitized.contains("data:text/html"));
    }

    @Test
    void preservesReadingSemanticsAndHardensLinks() {
        String sanitized = HtmlSanitizer.sanitize("""
                <table><tr><th scope="col">Term</th><td colspan="2">Meaning</td></tr></table>
                <a href="https://example.test/reference">Reference</a>
                <img src="https://example.test/diagram.png" alt="Diagram" width="400">
                """);

        assertTrue(sanitized.contains("<table>"));
        assertTrue(sanitized.contains("colspan=\"2\""));
        assertTrue(sanitized.contains("https://example.test/diagram.png"));
        assertTrue(sanitized.contains("rel=\"nofollow noopener noreferrer\""));
    }

    @Test
    void preservesSupportedWritingFormattingAndRemovesUnsafeCss() {
        String sanitized = HtmlSanitizer.sanitizeWritingReview("""
                <p style="text-align: center; position: fixed">
                  <strong>Clear</strong> <span style="background-color: #fef08a; font-size: 18px; background-image: url(javascript:alert(1))">overview</span>
                  <script>alert(1)</script>
                </p>
                """);

        assertTrue(sanitized.contains("<strong>Clear</strong>"));
        assertTrue(sanitized.contains("text-align: center"));
        assertTrue(sanitized.contains("background-color: #fef08a"));
        assertTrue(sanitized.contains("font-size: 18px"));
        assertFalse(sanitized.contains("position"));
        assertFalse(sanitized.contains("background-image"));
        assertFalse(sanitized.contains("javascript"));
        assertFalse(sanitized.contains("<script"));
    }
}
