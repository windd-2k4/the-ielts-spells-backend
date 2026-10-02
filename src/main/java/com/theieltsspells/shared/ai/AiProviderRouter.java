package com.theieltsspells.shared.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class AiProviderRouter {

    private final Map<AiProvider, AiModelClient> clients = new EnumMap<>(AiProvider.class);
    private final AiRoutingProperties properties;
    private final Map<ModelKey, CircuitState> circuits = new ConcurrentHashMap<>();
    private final Map<AiProvider, Instant> providerCooldowns = new ConcurrentHashMap<>();

    public AiProviderRouter(List<AiModelClient> clients, AiRoutingProperties properties) {
        for (AiModelClient client : clients) this.clients.put(client.provider(), client);
        this.properties = properties;
    }

    public Duration timeoutFor(AiTaskType taskType) {
        return properties.timeoutFor(taskType);
    }

    public <T> T execute(AiTaskType taskType, AiGenerationRequest request, AiResultDecoder<T> decoder) {
        return execute(taskType, properties.providersFor(taskType), request, decoder);
    }

    public <T> T execute(AiTaskType taskType, List<AiProvider> providerOrder,
                         AiGenerationRequest request, AiResultDecoder<T> decoder) {
        Throwable lastFailure = null;
        boolean configuredProviderFound = false;

        for (AiProvider provider : providerOrder) {
            AiModelClient client = clients.get(provider);
            if (client == null || !client.isConfigured()) {
                log.debug("AI provider {} is not configured; skipping it for {}", provider, taskType);
                continue;
            }
            configuredProviderFound = true;
            if (providerCircuitOpen(provider)) {
                log.debug("AI provider circuit is open for {}; trying the next provider", provider);
                continue;
            }
            List<String> models = properties.modelsFor(provider, taskType);
            if (models.isEmpty()) {
                log.warn("AI provider {} has no models configured for {}", provider, taskType);
                continue;
            }

            boolean providerConfigurationFailure = false;
            for (String model : models) {
                ModelKey key = new ModelKey(provider, model);
                if (circuitOpen(key)) {
                    log.debug("AI circuit is open for provider={} model={}; trying the next route", provider, model);
                    continue;
                }

                int maxAttempts = Math.max(1, properties.getRetry().getMaxRetriesPerModel() + 1);
                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                    long startedAt = System.nanoTime();
                    try {
                        AiGenerationResult result = client.generate(model, request);
                        T decoded = decoder.decode(result);
                        recordSuccess(key);
                        log.info("AI request succeeded task={} provider={} model={} attempt={} latencyMs={}",
                                taskType, provider, model, attempt, elapsedMillis(startedAt));
                        return decoded;
                    } catch (AiProviderException exception) {
                        lastFailure = exception;
                        log.warn("AI request failed task={} provider={} model={} attempt={} kind={} latencyMs={}",
                                taskType, provider, model, attempt, exception.kind(), elapsedMillis(startedAt));

                        if (exception.kind() == AiProviderException.Kind.INVALID_REQUEST) {
                            throw new AiRoutingException("AI request is invalid and cannot be retried", exception);
                        }
                        if (exception.kind() == AiProviderException.Kind.PROVIDER_CONFIGURATION) {
                            providerConfigurationFailure = true;
                            providerCooldowns.put(provider, Instant.now());
                            break;
                        }
                        if (exception.kind() != AiProviderException.Kind.TRANSIENT || attempt == maxAttempts) {
                            recordFailure(key);
                            break;
                        }
                        sleepBeforeRetry(attempt);
                    } catch (Exception exception) {
                        lastFailure = exception;
                        recordFailure(key);
                        log.warn("AI response validation failed task={} provider={} model={} latencyMs={}",
                                taskType, provider, model, elapsedMillis(startedAt));
                        break;
                    }
                }
                if (providerConfigurationFailure) {
                    log.warn("AI provider {} is misconfigured; moving to the next provider", provider);
                    break;
                }
            }
        }

        if (!configuredProviderFound) {
            throw new AiRoutingException("No AI provider is configured");
        }
        throw new AiRoutingException("All configured AI routes failed", lastFailure);
    }

    private boolean circuitOpen(ModelKey key) {
        CircuitState state = circuits.get(key);
        if (state == null || state.openedAt == null) return false;
        Duration cooldown = Duration.ofSeconds(Math.max(1,
                properties.getCircuitBreaker().getCooldownSeconds()));
        if (Instant.now().isAfter(state.openedAt.plus(cooldown))) {
            circuits.remove(key, state);
            return false;
        }
        return true;
    }

    private boolean providerCircuitOpen(AiProvider provider) {
        Instant openedAt = providerCooldowns.get(provider);
        if (openedAt == null) return false;
        Duration cooldown = Duration.ofSeconds(Math.max(1,
                properties.getCircuitBreaker().getCooldownSeconds()));
        if (Instant.now().isAfter(openedAt.plus(cooldown))) {
            providerCooldowns.remove(provider, openedAt);
            return false;
        }
        return true;
    }

    private void recordSuccess(ModelKey key) {
        circuits.remove(key);
        providerCooldowns.remove(key.provider());
    }

    private void recordFailure(ModelKey key) {
        int threshold = Math.max(1, properties.getCircuitBreaker().getFailureThreshold());
        circuits.compute(key, (ignored, current) -> {
            int failures = current == null ? 1 : current.failures + 1;
            return new CircuitState(failures, failures >= threshold ? Instant.now() : null);
        });
    }

    private void sleepBeforeRetry(int attempt) {
        long initial = Math.max(0, properties.getRetry().getInitialBackoffMillis());
        long maximum = Math.max(initial, properties.getRetry().getMaxBackoffMillis());
        long exponential = initial == 0 ? 0 : initial * (1L << Math.min(attempt - 1, 10));
        long capped = Math.min(exponential, maximum);
        long jitter = capped == 0 ? 0 : ThreadLocalRandom.current().nextLong(Math.max(1, capped / 4 + 1));
        try {
            Thread.sleep(Math.min(maximum, capped + jitter));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiRoutingException("AI fallback was interrupted", exception);
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record ModelKey(AiProvider provider, String model) { }

    private static final class CircuitState {
        private final int failures;
        private final Instant openedAt;

        private CircuitState(int failures, Instant openedAt) {
            this.failures = failures;
            this.openedAt = openedAt;
        }
    }
}
