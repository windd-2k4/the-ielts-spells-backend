package com.theieltsspells.testing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.ai.AiProviderRouter;
import com.theieltsspells.shared.ai.AiResultDecoder;
import com.theieltsspells.shared.ai.AiTaskType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SpeakingAdaptiveFollowUpServiceTests {

    private SpeakingAdaptiveFollowUpService serviceWith(AiProviderRouter router) {
        SpeakingAdaptiveFollowUpService service =
                new SpeakingAdaptiveFollowUpService(router, new ObjectMapper());
        ReflectionTestUtils.setField(service, "timeoutMs", 6_000L);
        return service;
    }

    private SpeakingAdaptiveFollowUpService.Context baseContext(String transcript, int remaining) {
        return new SpeakingAdaptiveFollowUpService.Context(
                "public-spaces",
                "Why do places change over time?",
                transcript,
                "Describe a memorable place.",
                "I often visit a quiet park near my river.",
                List.of(),
                List.of("Why do places change over time?", "How can cities preserve identity?"),
                remaining);
    }

    @Test
    void returnsValidatedProposalFromRouterJson() {
        AiProviderRouter router = Mockito.mock(AiProviderRouter.class);
        when(router.execute(eq(AiTaskType.CHAT), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    AiResultDecoder<?> decoder = invocation.getArgument(2);
                    return decoder.decode(new com.theieltsspells.shared.ai.AiGenerationResult(
                            com.theieltsspells.shared.ai.AiProvider.GEMINI,
                            "gemini-2.5-flash",
                            """
                            ```json
                            {"shouldFollowUp": true, "questionText": "Has urban redevelopment affected how local families use that park?", "rationale": "Candidate mentioned riverside park redevelopment."}
                            ```
                            """));
                });

        var service = serviceWith(router);
        var proposal = service.propose(baseContext(
                "I think commercial towers have replaced many old neighbourhood parks recently.", 2));

        assertThat(proposal).isPresent();
        assertThat(proposal.get().questionText())
                .isEqualTo("Has urban redevelopment affected how local families use that park?");
    }

    @Test
    void skipsRouterWhenTranscriptIsTooShortOrQuotaIsExhausted() {
        AiProviderRouter router = Mockito.mock(AiProviderRouter.class);
        var service = serviceWith(router);

        assertThat(service.propose(baseContext("Yes, I agree.", 2))).isEmpty();
        assertThat(service.propose(baseContext(
                "This is a sufficiently detailed candidate answer about urban development.", 0))).isEmpty();
        verifyNoInteractions(router);
    }

    @Test
    void rejectsFeedbackDuplicatesAndMalformedQuestions() {
        AiProviderRouter router = Mockito.mock(AiProviderRouter.class);
        var service = serviceWith(router);
        List<String> existing = List.of("How can cities preserve identity?");

        assertThat(service.decodeAndValidate(
                """
                {"shouldFollowUp": true, "questionText": "Good job! Your band score could improve if you explain more?"}
                """, existing)).isEmpty();
        assertThat(service.decodeAndValidate(
                """
                {"shouldFollowUp": true, "questionText": "How can cities preserve their identity?"}
                """, existing)).isEmpty();
        assertThat(service.decodeAndValidate(
                """
                {"shouldFollowUp": true, "questionText": "Tell me more about historic preservation"}
                """, existing)).isEmpty();
        assertThat(service.decodeAndValidate(
                """
                {"shouldFollowUp": true, "questionText": "Why?"}
                """, existing)).isEmpty();
        assertThat(service.decodeAndValidate(
                """
                {"shouldFollowUp": true, "questionText": "Why is heritage valuable? And who pays?"}
                """, existing)).isEmpty();
        assertThat(service.decodeAndValidate(
                """
                {"shouldFollowUp": false, "questionText": "Should communities fund local museums?"}
                """, existing)).isEmpty();
    }

    @Test
    void fallsBackGracefullyWhenRouterThrowsOrTimesOut() {
        AiProviderRouter router = Mockito.mock(AiProviderRouter.class);
        when(router.execute(eq(AiTaskType.CHAT), any(), any()))
                .thenThrow(new RuntimeException("Simulated timeout"));

        var service = serviceWith(router);
        var proposal = service.propose(baseContext(
                "Modern cities change fast because young people migrate toward new industrial districts.", 2));

        assertThat(proposal).isEmpty();
    }
}
