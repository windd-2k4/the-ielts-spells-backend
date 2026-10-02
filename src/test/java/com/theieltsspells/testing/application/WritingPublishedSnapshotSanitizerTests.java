package com.theieltsspells.testing.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WritingPublishedSnapshotSanitizerTests {
    @Test
    void sanitizesStudentAndSampleHtmlWithoutMutatingTheDraft() {
        String unsafe = "<p onclick=\"steal()\">Prompt</p><script>alert(1)</script><img src=\"javascript:alert(2)\">";
        var draft = Map.<String, Object>of("tasks", List.of(Map.of(
                "taskNo", 1,
                "promptHtml", unsafe,
                "sampleBand8Answer", "<p>Safe <strong>sample</strong></p><iframe src=\"https://evil.test\"></iframe>",
                "teacherNotes", "<script>kept as private plain data</script>"
        )));

        var published = WritingPublishedSnapshotSanitizer.sanitize(draft);
        @SuppressWarnings("unchecked")
        var task = (Map<String, Object>) ((List<?>) published.get("tasks")).getFirst();

        assertEquals(unsafe, ((Map<?, ?>) ((List<?>) draft.get("tasks")).getFirst()).get("promptHtml"));
        assertTrue(task.get("promptHtml").toString().contains("<p>Prompt</p>"));
        assertFalse(task.get("promptHtml").toString().contains("script"));
        assertFalse(task.get("promptHtml").toString().contains("javascript:"));
        assertTrue(task.get("sampleBand8Answer").toString().contains("<strong>sample</strong>"));
        assertFalse(task.get("sampleBand8Answer").toString().contains("iframe"));
    }
}
