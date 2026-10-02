package com.theieltsspells.testing.application;

import com.theieltsspells.shared.util.HtmlSanitizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creates a detached, safe snapshot for immutable Writing versions. */
final class WritingPublishedSnapshotSanitizer {

    private static final Set<String> HTML_FIELDS = Set.of("promptHtml", "sampleBand8Answer");

    private WritingPublishedSnapshotSanitizer() {
    }

    static Map<String, Object> sanitize(Map<String, Object> content) {
        return sanitizeMap(content == null ? Map.of() : content);
    }

    private static Map<String, Object> sanitizeMap(Map<?, ?> source) {
        var result = new LinkedHashMap<String, Object>();
        source.forEach((rawKey, value) -> {
            String key = String.valueOf(rawKey);
            result.put(key, HTML_FIELDS.contains(key) && value instanceof String html
                    ? HtmlSanitizer.sanitize(html)
                    : sanitizeValue(value));
        });
        return result;
    }

    private static Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) return sanitizeMap(map);
        if (value instanceof List<?> values) {
            var result = new ArrayList<>();
            values.forEach(item -> result.add(sanitizeValue(item)));
            return result;
        }
        return value;
    }
}
