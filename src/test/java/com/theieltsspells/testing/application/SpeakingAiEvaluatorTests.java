package com.theieltsspells.testing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.ai.AiAudioInput;
import com.theieltsspells.shared.ai.AiGenerationRequest;
import com.theieltsspells.shared.ai.AiGenerationResult;
import com.theieltsspells.shared.ai.AiProvider;
import com.theieltsspells.shared.ai.AiProviderRouter;
import com.theieltsspells.shared.ai.AiResultDecoder;
import com.theieltsspells.shared.ai.AiTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.theieltsspells.testing.domain.SpeakingSessionVariant;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeakingAiEvaluatorTests {

    @Mock
    private AiProviderRouter router;
    @Mock
    private SpeakingAudioService audioService;

    private SpeakingAiEvaluator evaluator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(router.timeoutFor(AiTaskType.CHAT)).thenReturn(Duration.ofSeconds(60));
        evaluator = new SpeakingAiEvaluator(router, objectMapper, audioService);
    }

    @Test
    void evaluatesFullResponseWithMetricsAndUpgrades() {
        String jsonPayload = """
                {
                  "criteria": {
                    "fluency_coherence": {"band": 6.5, "feedback": "Good continuity with occasional hesitations."},
                    "lexical_resource": {"band": 7.0, "feedback": "Wide range of vocabulary with good collocations."},
                    "grammatical_range_accuracy": {"band": 6.0, "feedback": "Mixture of simple and complex forms."},
                    "pronunciation": {"band": 6.5, "feedback": "Generally clear and easy to understand."}
                  },
                  "speech_metrics": {
                    "pause_count": 4,
                    "pronunciation_issues": [
                      {
                        "word": "comfortable",
                        "ipa": "/ˈkʌmftəbl/",
                        "issue": "Nuốt âm hoặc sai trọng âm"
                      }
                    ]
                  },
                  "answer_upgrades": [
                    {
                      "question_key": "part1_q1",
                      "original_text": "I like books because it good for brain.",
                      "upgraded_text": "I have a keen interest in reading because it helps broaden my horizons.",
                      "collocations": ["broaden my horizons", "keen interest in"],
                      "explanation": "Dùng collocations Band 7.5+ và sửa cấu trúc câu phức tự nhiên."
                    }
                  ],
                  "strengths": ["Clear pronunciation", "Good use of academic words"],
                  "improvements": ["Reduce pause duration", "Fix third-person verb agreement"]
                }
                """;

        when(audioService.openStored("test-audio.webm"))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));

        when(router.execute(eq(AiTaskType.CHAT), any(AiGenerationRequest.class), any()))
                .thenAnswer(invocation -> {
                    AiResultDecoder<SpeakingAiEvaluator.Result> decoder = invocation.getArgument(2);
                    return decoder.decode(new AiGenerationResult(AiProvider.GEMINI, "gemini-1.5-flash", jsonPayload));
                });

        var source = new SpeakingEvaluationService.ResponseSource(
                "part1_q1", "Um I like books because they help me think, you know.",
                "test-audio.webm", null, 30_000
        );

        var result = evaluator.evaluate(List.of(source));

        assertThat(result).isNotNull();
        assertThat(result.provider()).isEqualTo("GEMINI");
        assertThat(result.model()).isEqualTo("gemini-1.5-flash");
        assertThat(result.overall()).isEqualByComparingTo(new BigDecimal("6.5"));
        assertThat(result.fluency().band()).isEqualByComparingTo(new BigDecimal("6.5"));
        assertThat(result.lexical().band()).isEqualByComparingTo(new BigDecimal("7.0"));
        assertThat(result.grammar().band()).isEqualByComparingTo(new BigDecimal("6.0"));
        assertThat(result.pronunciation().band()).isEqualByComparingTo(new BigDecimal("6.5"));

        // Verify speech metrics
        assertThat(result.speechMetrics()).isNotNull();
        assertThat(result.speechMetrics().wordsPerMinute()).isEqualTo(22);
        assertThat(result.speechMetrics().pauseCount()).isEqualTo(4);
        assertThat(result.speechMetrics().pauseCountEstimated()).isTrue();
        assertThat(result.speechMetrics().fillerWords()).containsExactlyInAnyOrderEntriesOf(
                java.util.Map.of("um", 1, "you know", 1));
        assertThat(result.speechMetrics().pronunciationIssues()).hasSize(1);
        assertThat(result.speechMetrics().pronunciationIssues().get(0).word()).isEqualTo("comfortable");
        assertThat(result.speechMetrics().pronunciationIssues().get(0).ipa()).isEqualTo("ˈkʌmftəbl");

        // Verify answer upgrades
        assertThat(result.answerUpgrades()).hasSize(1);
        var upgrade = result.answerUpgrades().get(0);
        assertThat(upgrade.questionKey()).isEqualTo("part1_q1");
        assertThat(upgrade.originalText()).isEqualTo("I like books because it good for brain.");
        assertThat(upgrade.upgradedText()).isEqualTo("I have a keen interest in reading because it helps broaden my horizons.");
        assertThat(upgrade.collocations()).containsExactly("broaden my horizons", "keen interest in");

        // Verify strengths and improvements
        assertThat(result.strengths()).contains("Clear pronunciation");
        assertThat(result.improvements()).contains("Reduce pause duration");
    }

    @Test
    void handlesMissingMetricsGracefullyForBackwardCompatibility() {
        String jsonPayloadWithoutMetrics = """
                {
                  "criteria": {
                    "fluency_coherence": {"band": 6.0, "feedback": "Average fluency."},
                    "lexical_resource": {"band": 6.0, "feedback": "Adequate vocabulary."},
                    "grammatical_range_accuracy": {"band": 6.0, "feedback": "Basic structures."},
                    "pronunciation": {"band": 6.0, "feedback": "Mostly clear."}
                  },
                  "strengths": ["Willing to speak"],
                  "improvements": ["Practice more"]
                }
                """;

        when(audioService.openStored("test-audio.webm"))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));

        when(router.execute(eq(AiTaskType.CHAT), any(AiGenerationRequest.class), any()))
                .thenAnswer(invocation -> {
                    AiResultDecoder<SpeakingAiEvaluator.Result> decoder = invocation.getArgument(2);
                    return decoder.decode(new AiGenerationResult(AiProvider.GEMINI, "gemini-1.5-flash", jsonPayloadWithoutMetrics));
                });

        var source = new SpeakingEvaluationService.ResponseSource(
                "part1_q1", "Sample text", "test-audio.webm", null, null
        );

        var result = evaluator.evaluate(List.of(source), SpeakingSessionVariant.PART_1);

        assertThat(result).isNotNull();
        assertThat(result.overall()).isNull();
        assertThat(result.speechMetrics()).isNotNull();
        assertThat(result.speechMetrics().wordsPerMinute()).isNull();
        assertThat(result.speechMetrics().pauseCount()).isNull();
        assertThat(result.speechMetrics().pauseCountEstimated()).isFalse();
        assertThat(result.speechMetrics().fillerWords()).isEmpty();
        assertThat(result.speechMetrics().pronunciationIssues()).isEmpty();
        assertThat(result.answerUpgrades()).isEmpty();
    }
}
