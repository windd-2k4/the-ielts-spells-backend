package com.theieltsspells.writingevaluation.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.theieltsspells.shared.ai.AiGenerationRequest;
import com.theieltsspells.shared.ai.AiProviderRouter;
import com.theieltsspells.shared.ai.AiTaskType;
import com.theieltsspells.learninglibrary.MediaAssetContentReader;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WritingAiEvaluatorTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AiProviderRouter router = mock(AiProviderRouter.class);
    private final MediaAssetContentReader mediaAssets = mock(MediaAssetContentReader.class);
    private final WritingAiEvaluator evaluator = new WritingAiEvaluator(mapper, router, mediaAssets);

    @Test
    void computesPracticeBandAndRequiresEvidenceFromStoredEssay() throws Exception {
        var result = evaluator.decode("test", "test", valid().toString(), "task_2", "My essay.");
        assertEquals(0, new BigDecimal("6.5").compareTo(result.overallBand()));
        assertEquals(List.of("My essay."), result.criteria().get("task_response").evidence());
        var invented = valid();
        ((ObjectNode) invented.path("criteria").path("task_response")).putArray("evidence").add("Invented quotation");
        assertThrows(IllegalArgumentException.class, () -> evaluator.decode("test", "test", invented.toString(), "task_2", "My essay."));
    }

    @Test
    void rejectsEmptyCriterionEvidenceNeededForStudentHighlights() {
        var root = valid();
        ((ObjectNode) root.path("criteria").path("lexical_resource")).putArray("evidence");

        assertThrows(IllegalArgumentException.class,
                () -> evaluator.decode("test", "test", root.toString(), "task_2", "My essay."));
    }

    @Test
    void rejectsMissingStringAndOutOfRangeBandsInsteadOfCoercingToZero() {
        for (String bad : List.of("null", "\"6.5\"", "10", "-1", "6.3")) {
            var root = valid();
            try { ((ObjectNode) root.path("criteria").path("task_response")).set("band", mapper.readTree(bad)); }
            catch (Exception exception) { throw new AssertionError(exception); }
            assertThrows(IllegalArgumentException.class, () -> evaluator.decode("test", "test", root.toString(), "task_2", "My essay."));
        }
        var missing = valid();
        ((ObjectNode) missing.path("criteria").path("task_response")).remove("band");
        assertThrows(IllegalArgumentException.class, () -> evaluator.decode("test", "test", missing.toString(), "task_2", "My essay."));
    }

    @Test
    void rejectsWrongCriteriaAndInvalidConfidence() {
        assertThrows(IllegalArgumentException.class, () -> evaluator.decode("test", "test", valid().toString(), "academic_task_1", "My essay."));
        var root = valid().put("confidence", 1.1);
        assertThrows(IllegalArgumentException.class, () -> evaluator.decode("test", "test", root.toString(), "task_2", "My essay."));
    }

    @Test
    void doesNotCallTextModelWhenTaskContextIsMissingOrVisual() {
        assertThrows(WritingAiEvaluator.MissingContextException.class, () -> evaluator.evaluate("task_2", "essay", "{}"));
        assertThrows(WritingAiEvaluator.MissingContextException.class, () -> evaluator.evaluate("academic_task_1",
                "This answer deliberately contains more than twenty words so the visual validation runs before any model request is created successfully today.",
                "{\"promptHtml\":\"Describe this chart\",\"imageUrl\":\"https://example.test/chart.png\"}"));
        verifyNoInteractions(router);
    }

    @Test
    void appliesOfficialTwentyWordBoundaryWithoutCallingAModel() throws Exception {
        var result = evaluator.evaluate("task_2", "This response contains only a few words.",
                "{\"promptHtml\":\"Discuss both views.\"}");

        assertEquals(0, BigDecimal.ONE.compareTo(result.overallBand()));
        assertEquals("RULE_ENGINE", result.provider());
        assertTrue(result.criteria().values().stream().allMatch(value -> value.band().compareTo(BigDecimal.ONE) == 0));
        verifyNoInteractions(router, mediaAssets);
    }

    @Test
    void sendsTheManagedAcademicTaskOneVisualToTheModel() throws Exception {
        UUID assetId = UUID.randomUUID();
        var criteria = new LinkedHashMap<String, WritingAiEvaluator.Criterion>();
        for (String key : List.of("task_achievement", "coherence_cohesion", "lexical_resource", "grammatical_range_accuracy")) {
            criteria.put(key, new WritingAiEvaluator.Criterion(new BigDecimal("6.5"), "Feedback", List.of()));
        }
        var expected = new WritingAiEvaluator.Result("GEMINI", "test", new BigDecimal("6.5"), criteria,
                List.of(), List.of(), List.of(), null, mapper.createObjectNode());
        when(mediaAssets.readImage(assetId, 10L * 1024 * 1024))
                .thenReturn(new MediaAssetContentReader.ImageContent("image/png", new byte[]{1, 2, 3}));
        when(router.timeoutFor(AiTaskType.CHAT)).thenReturn(Duration.ofSeconds(5));
        when(router.execute(eq(AiTaskType.CHAT), any(AiGenerationRequest.class), any())).thenReturn(expected);

        var actual = evaluator.evaluate("academic_task_1",
                "This answer contains more than twenty words and describes the chart with a clear overview plus several relevant comparisons for assessment.",
                "{\"promptHtml\":\"Describe this chart\",\"imageUrl\":\"/admin/library/files/" + assetId + "/content\",\"imageAssetId\":\"" + assetId + "\"}");

        assertSame(expected, actual);
        var request = org.mockito.ArgumentCaptor.forClass(AiGenerationRequest.class);
        verify(router).execute(eq(AiTaskType.CHAT), request.capture(), any());
        assertEquals(1, request.getValue().images().size());
        assertEquals("image/png", request.getValue().images().getFirst().mimeType());
    }

    private ObjectNode valid() {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode criteria = root.putObject("criteria");
        for (String key : List.of("task_response", "coherence_cohesion", "lexical_resource", "grammatical_range_accuracy")) {
            criteria.putObject(key).put("band", "task_response".equals(key) ? 6 : 6.5)
                    .put("feedback", "Feedback").putArray("evidence").add("My essay.");
        }
        root.putArray("strengths").add("Clear position");
        root.putArray("improvements").add("Develop the argument");
        root.putArray("detectedErrors");
        root.put("overallBand", 9); // Deliberately ignored: the application owns aggregation.
        return root;
    }
}
