package com.theieltsspells.testing.application;

import com.theieltsspells.shared.util.HtmlSanitizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creates a detached, safe snapshot for immutable Reading versions. */
final class ReadingPublishedSnapshotSanitizer {

    private static final Set<String> HTML_FIELDS = Set.of(
            "content",
            "instructions",
            "prompt",
            "explanation",
            "trapAnalysis",
            "vocabularyNotes",
            "teacherNote",
            "text",
            "gapFillTemplate"
    );

    private static final Set<String> HTML_LIST_FIELDS = Set.of("reasoningSteps");

    private ReadingPublishedSnapshotSanitizer() {
    }

    static Map<String, Object> sanitize(Map<String, Object> content) {
        return sanitizeMap(content == null ? Map.of() : content);
    }

    private static Map<String, Object> sanitizeMap(Map<?, ?> source) {
        var result = new LinkedHashMap<String, Object>();
        source.forEach((rawKey, value) -> {
            String key = String.valueOf(rawKey);
            if (HTML_FIELDS.contains(key) && value instanceof String html) {
                result.put(key, HtmlSanitizer.sanitize(html));
            } else if (HTML_LIST_FIELDS.contains(key) && value instanceof List<?> values) {
                result.put(key, sanitizeHtmlList(values));
            } else {
                result.put(key, sanitizeValue(value));
            }
        });
        return result;
    }

    private static Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return sanitizeMap(map);
        }
        if (value instanceof List<?> values) {
            var result = new ArrayList<>();
            for (Object item : values) {
                result.add(sanitizeValue(item));
            }
            return result;
        }
        return value;
    }

    private static List<Object> sanitizeHtmlList(List<?> values) {
        var result = new ArrayList<Object>();
        for (Object value : values) {
            result.add(value instanceof String html ? HtmlSanitizer.sanitize(html) : sanitizeValue(value));
        }
        return result;
    }
}
