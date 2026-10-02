package com.theieltsspells.shared.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderRouterTests {

    private static final AiGenerationRequest REQUEST = new AiGenerationRequest(
            "system", "user", 256, 0.1, "application/json", false, Duration.ofSeconds(2)
    );

    @Test
    void timeoutRetriesThenFallsBackToNextProvider() {
        AiRoutingProperties properties = properties();
        AtomicInteger nvidiaCalls = new AtomicInteger();
        FakeClient nvidia = new FakeClient(AiProvider.NVIDIA, (model, request) -> {
            nvidiaCalls.incrementAndGet();
            throw new AiProviderException(AiProviderException.Kind.TRANSIENT, "timeout");
        });
        FakeClient gemini = new FakeClient(AiProvider.GEMINI,
                (model, request) -> new AiGenerationResult(AiProvider.GEMINI, model, "gemini-result"));

        AiProviderRouter router = new AiProviderRouter(List.of(nvidia, gemini), properties);

        String result = router.execute(AiTaskType.CHAT, REQUEST, AiGenerationResult::content);

        assertThat(result).isEqualTo("gemini-result");
        assertThat(nvidiaCalls).hasValue(2);
        assertThat(gemini.modelsCalled).containsExactly("gemini-chat");
    }

    @Test
    void unavailableModelFallsBackToNextModelOnSameProvider() {
        AiRoutingProperties properties = properties();
        properties.getNvidia().setQuizModels(new ArrayList<>(List.of("missing-model", "working-model")));
        FakeClient nvidia = new FakeClient(AiProvider.NVIDIA, (model, request) -> {
            if (model.equals("missing-model")) {
                throw new AiProviderException(AiProviderException.Kind.MODEL_UNAVAILABLE, "not found");
            }
            return new AiGenerationResult(AiProvider.NVIDIA, model, "quiz-result");
        });

        AiProviderRouter router = new AiProviderRouter(List.of(nvidia), properties);

        String result = router.execute(AiTaskType.QUIZ, List.of(AiProvider.NVIDIA),
                REQUEST, AiGenerationResult::content);

        assertThat(result).isEqualTo("quiz-result");
        assertThat(nvidia.modelsCalled).containsExactly("missing-model", "working-model");
    }

    @Test
    void openCircuitSkipsFailingModelOnFollowingRequest() {
        AiRoutingProperties properties = properties();
        properties.getRetry().setMaxRetriesPerModel(0);
        properties.getCircuitBreaker().setFailureThreshold(1);
        AtomicInteger nvidiaCalls = new AtomicInteger();
        FakeClient nvidia = new FakeClient(AiProvider.NVIDIA, (model, request) -> {
            nvidiaCalls.incrementAndGet();
            throw new AiProviderException(AiProviderException.Kind.TRANSIENT, "unavailable");
        });
        FakeClient gemini = new FakeClient(AiProvider.GEMINI,
                (model, request) -> new AiGenerationResult(AiProvider.GEMINI, model, "ok"));
        AiProviderRouter router = new AiProviderRouter(List.of(nvidia, gemini), properties);

        router.execute(AiTaskType.CHAT, REQUEST, AiGenerationResult::content);
        router.execute(AiTaskType.CHAT, REQUEST, AiGenerationResult::content);

        assertThat(nvidiaCalls).hasValue(1);
        assertThat(gemini.modelsCalled).hasSize(2);
    }

    @Test
    void invalidRequestStopsWithoutCallingAnotherProvider() {
        AiRoutingProperties properties = properties();
        FakeClient nvidia = new FakeClient(AiProvider.NVIDIA, (model, request) -> {
            throw new AiProviderException(AiProviderException.Kind.INVALID_REQUEST, "bad schema");
        });
        FakeClient gemini = new FakeClient(AiProvider.GEMINI,
                (model, request) -> new AiGenerationResult(AiProvider.GEMINI, model, "must-not-run"));
        AiProviderRouter router = new AiProviderRouter(List.of(nvidia, gemini), properties);

        assertThatThrownBy(() -> router.execute(AiTaskType.CHAT, REQUEST, AiGenerationResult::content))
                .isInstanceOf(AiRoutingException.class)
                .hasMessageContaining("invalid");
        assertThat(gemini.modelsCalled).isEmpty();
    }

    @Test
    void providerAuthenticationFailureFallsBackAndOpensProviderCircuit() {
        AiRoutingProperties properties = properties();
        AtomicInteger nvidiaCalls = new AtomicInteger();
        FakeClient nvidia = new FakeClient(AiProvider.NVIDIA, (model, request) -> {
            nvidiaCalls.incrementAndGet();
            throw new AiProviderException(AiProviderException.Kind.PROVIDER_CONFIGURATION, "unauthorized");
        });
        FakeClient gemini = new FakeClient(AiProvider.GEMINI,
                (model, request) -> new AiGenerationResult(AiProvider.GEMINI, model, "ok"));
        AiProviderRouter router = new AiProviderRouter(List.of(nvidia, gemini), properties);

        router.execute(AiTaskType.CHAT, REQUEST, AiGenerationResult::content);
        router.execute(AiTaskType.CHAT, REQUEST, AiGenerationResult::content);

        assertThat(nvidiaCalls).hasValue(1);
        assertThat(gemini.modelsCalled).hasSize(2);
    }

    private AiRoutingProperties properties() {
        AiRoutingProperties properties = new AiRoutingProperties();
        properties.setChatProviders(new ArrayList<>(List.of(AiProvider.NVIDIA, AiProvider.GEMINI)));
        properties.getNvidia().setChatModels(new ArrayList<>(List.of("nvidia-chat")));
        properties.getNvidia().setQuizModels(new ArrayList<>(List.of("nvidia-quiz")));
        properties.getGemini().setChatModels(new ArrayList<>(List.of("gemini-chat")));
        properties.getRetry().setMaxRetriesPerModel(1);
        properties.getRetry().setInitialBackoffMillis(0);
        properties.getRetry().setMaxBackoffMillis(0);
        return properties;
    }

    private static final class FakeClient implements AiModelClient {
        private final AiProvider provider;
        private final BiFunction<String, AiGenerationRequest, AiGenerationResult> behavior;
        private final List<String> modelsCalled = new ArrayList<>();

        private FakeClient(AiProvider provider,
                           BiFunction<String, AiGenerationRequest, AiGenerationResult> behavior) {
            this.provider = provider;
            this.behavior = behavior;
        }

        @Override
        public AiProvider provider() {
            return provider;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public AiGenerationResult generate(String model, AiGenerationRequest request) {
            modelsCalled.add(model);
            return behavior.apply(model, request);
        }
    }
}
