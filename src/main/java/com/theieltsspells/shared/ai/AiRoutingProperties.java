package com.theieltsspells.shared.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiRoutingProperties {

    private int connectTimeoutSeconds = 10;
    private int importTimeoutSeconds = 120;
    private int chatTimeoutSeconds = 30;
    private int quizTimeoutSeconds = 60;
    private List<AiProvider> importProviders = new ArrayList<>(List.of(AiProvider.GEMINI, AiProvider.NVIDIA));
    private List<AiProvider> chatProviders = new ArrayList<>(List.of(AiProvider.NVIDIA, AiProvider.GEMINI));
    private List<AiProvider> quizProviders = new ArrayList<>(List.of(AiProvider.NVIDIA, AiProvider.GEMINI));
    private Retry retry = new Retry();
    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    private Gemini gemini = new Gemini();
    private Nvidia nvidia = new Nvidia();

    public List<AiProvider> providersFor(AiTaskType taskType) {
        return switch (taskType) {
            case IMPORT -> List.copyOf(importProviders);
            case CHAT -> List.copyOf(chatProviders);
            case QUIZ -> List.copyOf(quizProviders);
        };
    }

    public List<String> modelsFor(AiProvider provider, AiTaskType taskType) {
        Provider providerProperties = provider == AiProvider.GEMINI ? gemini : nvidia;
        List<String> configured = switch (taskType) {
            case IMPORT -> providerProperties.getImportModels();
            case CHAT -> providerProperties.getChatModels();
            case QUIZ -> providerProperties.getQuizModels();
        };
        return configured.stream().map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
    }

    public Duration timeoutFor(AiTaskType taskType) {
        int seconds = switch (taskType) {
            case IMPORT -> importTimeoutSeconds;
            case CHAT -> chatTimeoutSeconds;
            case QUIZ -> quizTimeoutSeconds;
        };
        return Duration.ofSeconds(Math.max(1, Math.min(seconds, 600)));
    }

    @Getter
    @Setter
    public static class Retry {
        private int maxRetriesPerModel = 1;
        private long initialBackoffMillis = 250;
        private long maxBackoffMillis = 2_000;
    }

    @Getter
    @Setter
    public static class CircuitBreaker {
        private int failureThreshold = 3;
        private int cooldownSeconds = 30;
    }

    @Getter
    @Setter
    public static class Provider {
        private String apiKey = "";
        private String baseUrl = "";
        private List<String> importModels = new ArrayList<>();
        private List<String> chatModels = new ArrayList<>();
        private List<String> quizModels = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Gemini extends Provider {
        public Gemini() {
            setBaseUrl("https://generativelanguage.googleapis.com/v1beta");
            setImportModels(new ArrayList<>(List.of(
                    "gemini-3.5-flash-lite", "gemini-2.5-flash", "gemini-3.5-flash", "gemini-3.1-flash-lite"
            )));
            setChatModels(new ArrayList<>(List.of(
                    "gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-2.5-flash-lite", "gemini-2.5-flash"
            )));
            setQuizModels(new ArrayList<>(List.of(
                    "gemini-3.5-flash", "gemini-2.5-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite"
            )));
        }
    }

    @Getter
    @Setter
    public static class Nvidia extends Provider {
        public Nvidia() {
            setBaseUrl("https://integrate.api.nvidia.com/v1");
            List<String> defaults = new ArrayList<>(List.of(
                    "nvidia/nemotron-3-super-120b-a12b",
                    "nvidia/nemotron-3.5-lightning-30b-a3b",
                    "mistralai/mistral-nemotron"
            ));
            setImportModels(new ArrayList<>(defaults));
            setChatModels(new ArrayList<>(defaults));
            setQuizModels(new ArrayList<>(defaults));
        }
    }
}
